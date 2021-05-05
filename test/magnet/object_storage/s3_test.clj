;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns magnet.object-storage.s3-test
  (:require [clojure.java.io :as io]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer :all]
            [digest]
            [integrant.core :as ig]
            [magnet.object-storage.core :as core]
            [magnet.object-storage.s3]
            [org.httpkit.client :as http])
  (:import [com.amazonaws.services.s3.model AmazonS3Exception]
           [java.io File]
           [java.net URL]
           [java.security KeyPairGenerator]
           [java.security SecureRandom]
           [java.util UUID]
           [javax.crypto KeyGenerator]
           [magnet.object_storage.s3 AWSS3Bucket]))

(defn enable-instrumentation [f]
  (-> (stest/enumerate-namespace 'magnet.object-storage.s3) stest/instrument)
  (f))

(def presigned-url-lifespan 1)
(def config {:bucket-name (System/getenv "TEST_OBJECT_STORAGE_S3_BUCKET")
             :presigned-url-lifespan presigned-url-lifespan})
(def test-file-1-path "test-file-1")
(def test-file-2-path "test-file-2")

(defn setup []
  (spit test-file-1-path {:hello :world})
  (spit test-file-2-path [:apples :bananas]))

(defn teardown []
  (io/delete-file test-file-1-path)
  (io/delete-file test-file-2-path))

(defn with-test-files [f]
  (setup)
  (f)
  (teardown))

(use-fixtures :once enable-instrumentation)
(use-fixtures :each with-test-files)

(def aes256-key
  (let [kg (KeyGenerator/getInstance "AES")]
    (.init kg 256 (SecureRandom.))
    (.generateKey kg)))

(def another-aes256-key
  (let [kg (KeyGenerator/getInstance "AES")]
    (.init kg 256 (SecureRandom.))
    (.generateKey kg)))

(def rsa2048-key-pair
  (let [kg (KeyPairGenerator/getInstance "RSA")]
    (.initialize kg 2048 (SecureRandom.))
    (.generateKeyPair kg)))

(deftest protocol-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)]
    (testing "ig/init-key returns the right type of record for the boundary"
      (is (= (class s3-boundary)
             AWSS3Bucket)))))

(deftest ^:integration put-get-file-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test-" (UUID/randomUUID))
        put-result (core/put-object s3-boundary file-key (io/file test-file-1-path))]
    (testing "testing put-object"
      (is (:success? put-result)))
    (testing "testing get-object"
      (let [get-result (core/get-object s3-boundary file-key)]
        (is (:success? get-result))
        (is (= (digest/sha-256 (File. test-file-1-path))
               (digest/sha-256 (:object get-result))))))
    (core/delete-object s3-boundary file-key)))

(deftest ^:integration put-get-stream-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test-" (UUID/randomUUID))
        bytes (.getBytes "Test message")
        stream (io/input-stream bytes)
        put-result (core/put-object s3-boundary
                                    file-key
                                    stream
                                    {:metadata {:object-size (count bytes)}})]
    (testing "testing put object stream"
      (is (:success? put-result)))
    (testing "testing get object stream"
      (let [get-result (core/get-object s3-boundary file-key)]
        (is (:success? get-result))
        (is (= (digest/sha-256 bytes)
               (digest/sha-256 (slurp (:object get-result)))))))
    (core/delete-object s3-boundary file-key)))

(deftest ^:integration copy-get-file-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        source-file-key (str "integration-test-" (UUID/randomUUID))
        put-result (core/put-object s3-boundary source-file-key (io/file test-file-1-path))]
    (testing "testing put-object"
      (is (:success? put-result)))
    (testing "Successful copy and get object"
      (let [destination-file-key (str "integration-test-" (UUID/randomUUID))
            copy-result (core/copy-object s3-boundary source-file-key destination-file-key)]
        (testing "testing copy-object"
          (is (:success? copy-result)))
        (testing "testing get-object on copy"
          (let [get-result (core/get-object s3-boundary destination-file-key)]
            (is (:success? get-result))
            (is (= (digest/sha-256 (File. test-file-1-path))
                   (digest/sha-256 (:object get-result))))))
        (core/delete-object s3-boundary destination-file-key)))
    (testing "Failing copy because of source object replace attempt"
      (let [destination-file-key source-file-key
            copy-result (core/copy-object s3-boundary source-file-key destination-file-key)]
        (is (and (= false (:success? copy-result))
                 (= 400 (get-in copy-result [:error-details :status-code]))))))
    (core/delete-object s3-boundary source-file-key)))

(deftest ^:integration delete-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test-" (UUID/randomUUID))]
    (core/put-object s3-boundary file-key (io/file test-file-1-path))
    (core/delete-object s3-boundary file-key)
    (let [result (core/get-object s3-boundary file-key)]
      (testing "Attempt to get an object that's been deleted should throw an exception."
        (is (not (:success? result)))
        (is (= (get-in result [:error-details :error-code])
               "NoSuchKey"))))
    (let [result (core/delete-object s3-boundary (str (UUID/randomUUID)))]
      (testing "Amazonica is expected to allow deletion of a file that doesn't exist."
        (is (:success? result))))))

(deftest ^:integration list-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test/integration-test-" (UUID/randomUUID))
        file-key-2 (str "integration-test-2/integration-test-" (UUID/randomUUID))]
    (core/put-object s3-boundary file-key (io/file test-file-1-path))
    (core/put-object s3-boundary file-key-2 (io/file test-file-1-path))
    (let [result (core/list-objects s3-boundary "integration-test/")]
      (testing "list-objects test"
        (is (:success? result))
        (is (some (fn [{:keys [object-id]}]
                    (= object-id file-key))
                  (:objects result)))
        (is (not-any? (fn [{:keys [object-id]}]
                        (= object-id file-key-2))
                      (:objects result)))
        (is (every? #(and (:object-id %)
                          (:last-modified %)
                          (:size %))
                    (:objects result)))))))

(deftest ^:integration replace-object-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test-" (UUID/randomUUID))
        f1 (File. test-file-1-path)
        f2 (File. test-file-2-path)]
    (testing "It should be possible to replace an object."
      (let [file-upload-result (core/put-object s3-boundary file-key f1)
            file-2-upload-result (core/put-object s3-boundary file-key f2)
            get-result (core/get-object s3-boundary file-key)]
        (is (:success? file-upload-result))
        (is (:success? file-2-upload-result))
        (is (:success? get-result))
        (is (= (digest/sha-256 f2)
               (digest/sha-256 (slurp (:object get-result)))))))
    (core/delete-object s3-boundary file-key)))

(defn http-request
  [request]
  (let [request (assoc request :as :auto)
        {:keys [error status] :as response} @(http/request request)]
    (if error
      :error
      (if (<= 200 status 299)
        response
        :forbidden))))

(deftest ^:integration presigned-url-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test-" (UUID/randomUUID))
        f (File. test-file-1-path)]
    (core/put-object s3-boundary file-key f)
    (testing "testing default presigned url (defaults to :read operation)"
      (let [result (core/get-object-url s3-boundary file-key)
            url (:object-url result)]
        (is (:success? result))
        (is (string? url))
        (is (URL. url))
        (is (= (digest/sha-256 f)
               (digest/sha-256 (:body (http-request {:url url :method :get})))))))
    (testing "testing presigned url for :create method"
      (let [new-file-key (str file-key "-new")
            result (core/get-object-url s3-boundary new-file-key {:method :create})
            url (:object-url result)]
        (is (:success? result))
        (is (string? url))
        (is (URL. url))
        (is (not= :forbidden
                  (http-request {:url url
                                 :method :put
                                 :body (slurp f)})))
        (core/delete-object s3-boundary new-file-key)))
    (testing "testing :create presigned url throws exception when using :read method"
      (let [result (core/get-object-url s3-boundary file-key {:method :create})
            url (:object-url result)]
        (is (:success? result))
        (is (string? url))
        (is (URL. url))
        (is (= :forbidden
               (http-request {:url url :method :get})))))
    (testing "testing :read presigned url with specific filename"
      (let [result (core/get-object-url s3-boundary file-key {:filename "asdfasdf.docx"})
            url (:object-url result)]
        (is (:success? result))
        (is (string? url))
        (is (URL. url))
        (is (= "attachment; filename=asdfasdf.docx"
               (get-in (http-request {:url url :method :get}) [:headers :content-disposition])))))
    (core/delete-object s3-boundary file-key)))

(deftest ^:integration encrypted-put-get-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        file-key (str "integration-test-" (UUID/randomUUID))
        f (File. test-file-1-path)]
    (testing "testing encrypted f put-get"
      (let [put-result-rsa (core/put-object s3-boundary
                                            file-key f {:encryption {:key-pair rsa2048-key-pair}})
            get-result-no-key (core/get-object s3-boundary file-key)
            get-result-rsa (core/get-object s3-boundary
                                            file-key {:encryption {:key-pair rsa2048-key-pair}})
            put-result-aes (core/put-object s3-boundary
                                            file-key f {:encryption {:secret-key aes256-key}})
            get-result-aes (core/get-object s3-boundary
                                            file-key {:encryption {:secret-key aes256-key}})
            get-result-aes-wrong-key (core/get-object s3-boundary
                                                      file-key {:encryption {:secret-key another-aes256-key}})]
        (is (:success? put-result-rsa))
        (is (:success? put-result-aes))
        (is (:success? get-result-no-key))
        (is (:success? get-result-rsa))
        (is (:success? get-result-aes))
        (is (and (= false (:success? get-result-aes-wrong-key))
                 (= "Client" (get-in get-result-aes-wrong-key [:error-details :error-type]))))
        (is (not= (digest/sha-256 f)
                  (digest/sha-256 (slurp (:object get-result-no-key)))))
        (is (= (digest/sha-256 f)
               (digest/sha-256 (slurp (:object get-result-rsa)))))
        (is (= (digest/sha-256 f)
               (digest/sha-256 (slurp (:object get-result-aes)))))))
    (core/delete-object s3-boundary file-key)))

(deftest ^:integration encrypted-copy-get-test
  (let [s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        source-file-key (str "integration-test-" (UUID/randomUUID))
        destination-file-key (str "integration-test-" (UUID/randomUUID))
        f (File. test-file-1-path)]
    (testing "testing get encrypted object, copy it and get it back"
      (let [put-result-aes (core/put-object s3-boundary source-file-key f {:encryption {:secret-key aes256-key}})
            copy-result (core/copy-object s3-boundary source-file-key destination-file-key)
            get-copy-result-aes (core/get-object s3-boundary
                                                 destination-file-key
                                                 {:encryption {:secret-key aes256-key}})
            get-result-no-key (core/get-object s3-boundary destination-file-key)
            get-result-aes-wrong-key (core/get-object s3-boundary
                                                      destination-file-key
                                                      {:encryption {:secret-key another-aes256-key}})]
        (is (:success? put-result-aes))
        (is (:success? copy-result))
        (is (:success? get-copy-result-aes))
        (is (:success? get-result-no-key))
        (is (and (= false (:success? get-result-aes-wrong-key))
                 (= "Client" (get-in get-result-aes-wrong-key [:error-details :error-type]))))
        (is (not= (digest/sha-256 f)
                  (digest/sha-256 (slurp (:object get-result-no-key)))))
        (is (= (digest/sha-256 f)
               (digest/sha-256 (slurp (:object get-copy-result-aes)))))))
    (core/delete-object s3-boundary source-file-key)
    (core/delete-object s3-boundary destination-file-key)))
