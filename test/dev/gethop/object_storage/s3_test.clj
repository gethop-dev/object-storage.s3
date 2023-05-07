;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns dev.gethop.object-storage.s3-test
  (:require [clojure.java.io :as io]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer :all]
            [dev.gethop.object-storage.core :as core]
            [dev.gethop.object-storage.s3]
            [digest]
            [integrant.core :as ig]
            [org.httpkit.client :as http])
  (:import [dev.gethop.object_storage.s3 AWSS3Bucket]
           [java.io File]
           [java.net URL]
           [java.security KeyPairGenerator]
           [java.security SecureRandom]
           [java.util UUID]
           [javax.crypto KeyGenerator]))

(defn enable-instrumentation [f]
  (-> (stest/enumerate-namespace 'dev.gethop.object-storage.s3) stest/instrument)
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
  (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 config)]
    (testing "ig/init-key returns the right type of record for the boundary"
      (is (= (class s3-boundary)
             AWSS3Bucket)))))

(deftest ^:integration put-get-file-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        config-with-endpoint (assoc config :endpoint endpoint)]
    (doseq [current-config [config config-with-endpoint]]
      (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 current-config)
            file-key (str "integration-test-" (UUID/randomUUID))
            put-result (core/put-object s3-boundary file-key (io/file test-file-1-path))]
        (testing "testing put-object"
          (is (:success? put-result)))
        (testing "testing get-object"
          (let [get-result (core/get-object s3-boundary file-key)]
            (is (:success? get-result))
            (is (= (digest/sha-256 (File. ^String test-file-1-path))
                   (digest/sha-256 (:object get-result))))))
        (core/delete-object s3-boundary file-key)))))

(deftest ^:integration put-get-stream-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        config-with-endpoint (assoc config :endpoint endpoint)]
    (doseq [current-config [config config-with-endpoint]]
      (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 current-config)
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
        (core/delete-object s3-boundary file-key)))))

(deftest ^:integration copy-get-file-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        config-with-endpoint (assoc config :endpoint endpoint)]
    (doseq [current-config [config config-with-endpoint]]
      (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 current-config)
            src-key (str "integration-test-" (UUID/randomUUID))
            put-result (core/put-object s3-boundary src-key (io/file test-file-1-path))]
        (testing "testing put-object"
          (is (:success? put-result)))
        (testing "Successful copy and get object"
          (let [dst-key (str "integration-test-" (UUID/randomUUID))
                copy-result (core/copy-object s3-boundary src-key dst-key)]
            (testing "testing copy-object"
              (is (:success? copy-result)))
            (testing "testing get-object on copy"
              (let [get-result (core/get-object s3-boundary dst-key)]
                (is (:success? get-result))
                (is (= (digest/sha-256 (File. ^String test-file-1-path))
                       (digest/sha-256 (:object get-result))))))
            (core/delete-object s3-boundary dst-key)))
        (testing "Failing copy because of source object replace attempt"
          (let [destination-key src-key
                copy-result (core/copy-object s3-boundary src-key destination-key)]
            (is (and (= false (:success? copy-result))
                     (= 400 (get-in copy-result [:error-details :status-code]))))))
        (core/delete-object s3-boundary src-key)))))

(deftest ^:integration delete-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        config-with-endpoint (assoc config :endpoint endpoint)]
    (doseq [current-config [config config-with-endpoint]]
      (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 current-config)
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
            (is (:success? result))))))))

(deftest ^:integration list-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        config-with-endpoint (assoc config :endpoint endpoint)]
    (doseq [current-config [config config-with-endpoint]]
      (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 current-config)
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
                        (:objects result)))))
        (core/delete-object s3-boundary file-key)
        (core/delete-object s3-boundary file-key-2)))))

(deftest ^:integration replace-object-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        config-with-endpoint (assoc config :endpoint endpoint)]
    (doseq [current-config [config config-with-endpoint]]
      (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 current-config)
            file-key (str "integration-test-" (UUID/randomUUID))
            f1 (File. ^String test-file-1-path)
            f2 (File. ^String test-file-2-path)]
        (testing "It should be possible to replace an object."
          (let [file-upload-result (core/put-object s3-boundary file-key f1)
                file-2-upload-result (core/put-object s3-boundary file-key f2)
                get-result (core/get-object s3-boundary file-key)]
            (is (:success? file-upload-result))
            (is (:success? file-2-upload-result))
            (is (:success? get-result))
            (is (= (digest/sha-256 f2)
                   (digest/sha-256 (slurp (:object get-result)))))))
        (core/delete-object s3-boundary file-key)))))

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
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        config-with-endpoint (assoc config :endpoint endpoint)]
    (doseq [current-config [config config-with-endpoint]]
      (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 current-config)
            file-key (str "integration-test-" (UUID/randomUUID))
            f (File. ^String test-file-1-path)]
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
                url (:object-url result)
                http-response (http-request {:url url :method :get})]
            (is (:success? result))
            (is (string? url))
            (is (URL. url))
            (is (= "attachment; filename=asdfasdf.docx"
                   (get-in http-response [:headers :content-disposition])))))
        (core/delete-object s3-boundary file-key)))))

(deftest ^:integration encrypted-put-get-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        config-with-endpoint (assoc config :endpoint endpoint)]
    (doseq [current-config [config config-with-endpoint]]
      (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 current-config)
            file-key (str "integration-test-" (UUID/randomUUID))
            f (File. ^String test-file-1-path)]
        (testing "testing encrypted f put-get"
          (let [rsa-encrypt {:encryption {:key-pair rsa2048-key-pair}}
                aes-encrypt {:encryption {:secret-key aes256-key}}
                wrong-aes-encrypt {:encryption {:secret-key another-aes256-key}}
                put-rsa (core/put-object s3-boundary file-key f rsa-encrypt)
                get-no-key (core/get-object s3-boundary file-key)
                get-rsa (core/get-object s3-boundary file-key rsa-encrypt)
                put-aes (core/put-object s3-boundary file-key f aes-encrypt)
                get-aes (core/get-object s3-boundary file-key aes-encrypt)
                get-wrong-aes (core/get-object s3-boundary file-key wrong-aes-encrypt)]
            (is (:success? put-rsa))
            (is (:success? put-aes))
            (is (:success? get-no-key))
            (is (:success? get-rsa))
            (is (:success? get-aes))
            (is (and (= false (:success? get-wrong-aes))
                     (= "Client" (get-in get-wrong-aes [:error-details :error-type]))))
            (is (not= (digest/sha-256 f)
                      (digest/sha-256 (slurp (:object get-no-key)))))
            (is (= (digest/sha-256 f)
                   (digest/sha-256 (slurp (:object get-rsa)))))
            (is (= (digest/sha-256 f)
                   (digest/sha-256 (slurp (:object get-aes)))))))
        (core/delete-object s3-boundary file-key)))))

(deftest ^:integration encrypted-copy-get-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        config-with-endpoint (assoc config :endpoint endpoint)]
    (doseq [current-config [config config-with-endpoint]]
      (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 current-config)
            src-key (str "integration-test-" (UUID/randomUUID))
            dst-key (str "integration-test-" (UUID/randomUUID))
            f (File. ^String test-file-1-path)
            aes-encrypt {:encryption {:secret-key aes256-key}}
            wrong-aes-encrypt {:encryption {:secret-key another-aes256-key}}]
        (testing "testing get encrypted object, copy it and get it back"
          (let [put-result-aes (core/put-object s3-boundary src-key f aes-encrypt)
                copy-result (core/copy-object s3-boundary src-key dst-key)
                get-aes (core/get-object s3-boundary dst-key aes-encrypt)
                get-no-key (core/get-object s3-boundary dst-key)
                get-wrong-aes (core/get-object s3-boundary dst-key wrong-aes-encrypt)]
            (is (:success? put-result-aes))
            (is (:success? copy-result))
            (is (:success? get-aes))
            (is (:success? get-no-key))
            (is (and (= false (:success? get-wrong-aes))
                     (= "Client" (get-in get-wrong-aes [:error-details :error-type]))))
            (is (not= (digest/sha-256 f)
                      (digest/sha-256 (slurp (:object get-no-key)))))
            (is (= (digest/sha-256 f)
                   (digest/sha-256 (slurp (:object get-aes)))))))
        (core/delete-object s3-boundary src-key)
        (core/delete-object s3-boundary dst-key)))))
