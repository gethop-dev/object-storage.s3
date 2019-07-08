;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns magnet.object-storage.s3-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :refer [delete-file file input-stream]]
            [digest]
            [integrant.core :as ig]
            [magnet.object-storage.core :as core]
            [magnet.object-storage.s3])
  (:import [com.amazonaws.services.s3.model AmazonS3Exception]
           [java.io File]
           [java.io IOException]
           [java.net URL]
           [java.security KeyPairGenerator]
           [java.security SecureRandom]
           [java.util UUID]
           [magnet.object_storage.s3 AWSS3Bucket]))

(def config {:bucket-name (System/getenv "OBJECT_STORAGE_S3_BUCKET")})
(def test-file-1-path "test-file-1")
(def test-file-2-path "test-file-2")

(defn setup []
  (spit test-file-1-path {:hello :world})
  (spit test-file-2-path [:apples :bananas]))

(defn teardown []
  (delete-file test-file-1-path)
  (delete-file test-file-2-path))

(defn with-test-files [f]
  (setup)
  (f)
  (teardown))

(use-fixtures :each with-test-files)

(def key-pair
  (let [kg (KeyPairGenerator/getInstance "RSA")]
    (.initialize kg 1024 (SecureRandom.))
    (.generateKeyPair kg)))

(deftest protocol-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)]
    (is
     (= (class s3-boundary)
        AWSS3Bucket))))

(deftest ^:integration put-get-file-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test-" (UUID/randomUUID))]
    (testing "testing put-object"
      (is (core/put-object s3-boundary file-key (file test-file-1-path))))
    (testing "testing get-object"
      (let [result (core/get-object s3-boundary file-key)]
        (is result)
        (= (digest/sha-256 (File. test-file-1-path))
           (digest/sha-256 result))))
    (core/delete-object s3-boundary file-key)))

(deftest ^:integration put-get-stream-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test-" (UUID/randomUUID))
        bytes (.getBytes "Test message")
        stream (input-stream bytes)]
    (testing "testing put object stream"
      (is (core/put-object s3-boundary file-key nil {:input-stream stream
                                                     :metadata {:content-length (count bytes)}})))
    (testing "testing get object stream"
      (let [result (core/get-object s3-boundary file-key)]
        (is result)
        (= (digest/sha-256 bytes)
           (digest/sha-256 result))))
    (core/delete-object s3-boundary file-key)))

(deftest ^:integration delete-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test-" (UUID/randomUUID))]
    (core/put-object s3-boundary file-key (file test-file-1-path))
    (core/delete-object s3-boundary file-key)
    (is
     (thrown? AmazonS3Exception
              (core/get-object s3-boundary file-key))
     "Attempt to get an object that's been deleted should throw an exception.")
    (is
     (nil? (core/delete-object s3-boundary (str (UUID/randomUUID))))
     "Amazonica is expected to allow deletion of a file that doesn't exist.")))

(deftest ^:integration replace-object-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test-" (UUID/randomUUID))
        file (File. test-file-1-path)
        file2 (File. test-file-2-path)]
    (is
     (let [file-upload-result (core/put-object s3-boundary file-key file)
           file-2-upload-result (core/put-object s3-boundary file-key file2)]
       (= (digest/sha-256 (File. test-file-2-path))
          (digest/sha-256 (core/get-object s3-boundary file-key))))
     "It should be possible to replace an object.")
    (core/delete-object s3-boundary file-key)))

(deftest ^:integration presigned-url-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test-" (UUID/randomUUID))
        file (file test-file-1-path)]
    (core/put-object s3-boundary file-key file)
    (testing "testing default presigned url"
      (let [result (core/get-object-url s3-boundary file-key)]
        (is (string? result))
        (is (URL. result))
        (is (= (digest/sha-256 file)
               (digest/sha-256 (slurp result))))))
    (testing "testing POST presigned url throws exception when using GET"
      (let [result (core/get-object-url s3-boundary file-key {:method "POST"})]
        (is (string? result))
        (is (URL. result))
        (is (thrown? IOException
                     (slurp result)))))
    (core/delete-object s3-boundary file-key)))

(deftest ^:integration encrypted-put-get-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test-" (UUID/randomUUID))
        file (file test-file-1-path)]
    (testing "testing encrypted file put-get"
        (core/put-object s3-boundary file-key file {:encryption {:key-pair key-pair}})
      (is (not (=
                (digest/sha-256 file)
                (digest/sha-256 (core/get-object s3-boundary file-key)))))
      (is (=
           (digest/sha-256 file)
           (digest/sha-256 (core/get-object s3-boundary file-key {:encryption {:key-pair key-pair}})))))
    (core/delete-object s3-boundary file-key)))
