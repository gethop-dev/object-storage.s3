;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns dev.gethop.object-storage.s3-test
  (:require [clojure.java.io :as io]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer :all]
            [dev.gethop.object-storage.core :as core]
            [dev.gethop.object-storage.s3 :as s3]
            [digest]
            [integrant.core :as ig]
            [org.httpkit.client :as http])
  (:import (dev.gethop.object_storage.s3 AWSS3Bucket)
           (java.io File)
           (java.net URI)
           (java.util UUID)))

(defn- enable-instrumentation [f]
  (-> (stest/enumerate-namespace 'dev.gethop.object-storage.s3) stest/instrument)
  (f))

#_{:clj-kondo/ignore [:missing-docstring]}
(def presigned-url-lifespan 1)
#_{:clj-kondo/ignore [:missing-docstring]}
(def config {:bucket-name (System/getenv "TEST_OBJECT_STORAGE_S3_BUCKET")
             :presigned-url-lifespan presigned-url-lifespan})
#_{:clj-kondo/ignore [:missing-docstring]}
(def test-file-1-path "test-file-1")
#_{:clj-kondo/ignore [:missing-docstring]}
(def test-file-2-path "test-file-2")
#_{:clj-kondo/ignore [:missing-docstring]}
(def canned-acl "public-read")

(defn- setup []
  (spit test-file-1-path {:hello :world})
  (spit test-file-2-path [:apples :bananas]))

(defn- teardown []
  (io/delete-file test-file-1-path)
  (io/delete-file test-file-2-path))

(defn- with-test-files [f]
  (setup)
  (f)
  (teardown))

(use-fixtures :once enable-instrumentation)
(use-fixtures :each with-test-files)

(deftest protocol-test
  (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 config)]
    (testing "ig/init-key returns the right type of record for the boundary"
      (is (= (class s3-boundary)
             AWSS3Bucket)))))

(deftest ^:integration put-get-public-file-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        endpoint-region (System/getenv "TEST_OBJECT_STORAGE_S3_REGION")
        config-with-endpoint (cond-> (-> config
                                         (assoc :endpoint endpoint)
                                         (assoc :explicit-object-acl canned-acl))
                               endpoint-region
                               (assoc :endpoint-region endpoint-region))]
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

(deftest ^:integration put-get-file-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        endpoint-region (System/getenv "TEST_OBJECT_STORAGE_S3_REGION")
        config-with-endpoint (cond-> (assoc config :endpoint endpoint)
                               endpoint-region
                               (assoc :endpoint-region endpoint-region))]
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
        endpoint-region (System/getenv "TEST_OBJECT_STORAGE_S3_REGION")
        config-with-endpoint (cond-> (assoc config :endpoint endpoint)
                               endpoint-region
                               (assoc :endpoint-region endpoint-region))]
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

(deftest ^:integration put-get-with-content-disposition-content-type-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        endpoint-region (System/getenv "TEST_OBJECT_STORAGE_S3_REGION")
        config-with-endpoint (cond-> (assoc config :endpoint endpoint)
                               endpoint-region
                               (assoc :endpoint-region endpoint-region))]
    (doseq [current-config [config config-with-endpoint]]
      (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 current-config)
            file-key-attachment (str "integration-test-" (UUID/randomUUID))
            file-key-inline (str "integration-test-" (UUID/randomUUID))
            bytes (.getBytes "Test message")
            metadata {:object-size (count bytes)
                      :content-type "image/png"}]
        (testing "testing put object as attachment"
          (let [metadata (assoc metadata
                                :content-disposition :attachment
                                :filename file-key-attachment)
                stream (io/input-stream bytes)
                put-result (core/put-object s3-boundary
                                            file-key-attachment
                                            stream
                                            {:metadata metadata})]
            (is (:success? put-result))))
        (testing "testing get object stream with object metadata for attachment content disposition"
          (let [get-result (core/get-object s3-boundary file-key-attachment)]
            (is (:success? get-result))
            (is (= (digest/sha-256 bytes)
                   (digest/sha-256 (slurp (:object get-result)))))
            ;; content-disposition is a keyword for the pub-object
            ;; request (to indicate what kind of content-disposition
            ;; we want), but it's the full content-disposition HTTP
            ;; header in the get-object response. Thus, we need to
            ;; compare those two things accordingly.
            (is (= metadata
                   (-> (:metadata get-result)
                       (select-keys (keys (dissoc metadata :content-disposition))))))
            (is (re-find #"^attachment;" (-> get-result :metadata :content-disposition)))))
        (testing "testing put object as inline"
          (let [metadata (assoc metadata
                                :content-disposition :inline
                                :filename file-key-inline)
                stream (io/input-stream bytes)
                put-result (core/put-object s3-boundary
                                            file-key-inline
                                            stream
                                            {:metadata metadata})]
            (is (:success? put-result))))
        (testing "testing get object stream with object metadata for inline content disposition"
          (let [get-result (core/get-object s3-boundary file-key-inline)]
            (is (:success? get-result))
            (is (= (digest/sha-256 bytes)
                   (digest/sha-256 (slurp (:object get-result)))))
            ;; content-disposition is a keyword for the pub-object
            ;; request (to indicate what kind of content-disposition
            ;; we want), but it's the full content-disposition HTTP
            ;; header in the get-object response. Thus, we need to
            ;; compare those two things accordingly.
            (is (= metadata
                   (-> (:metadata get-result)
                       (select-keys (keys (dissoc metadata :content-disposition))))))
            (is (re-find #"^inline" (-> get-result :metadata :content-disposition)))))
        (doseq [file-key [file-key-attachment file-key-inline]]
          (core/delete-object s3-boundary file-key))))))

(deftest ^:integration copy-get-file-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        endpoint-region (System/getenv "TEST_OBJECT_STORAGE_S3_REGION")
        config-with-endpoint (cond-> (assoc config :endpoint endpoint)
                               endpoint-region
                               (assoc :endpoint-region endpoint-region))]
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
        (testing "Copying an object to itself succeeds (but does nothing)"
          (let [destination-key src-key
                copy-result (core/copy-object s3-boundary src-key destination-key)]
            (is (:success? copy-result))))
        (core/delete-object s3-boundary src-key)))))

(deftest ^:integration delete-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        endpoint-region (System/getenv "TEST_OBJECT_STORAGE_S3_REGION")
        config-with-endpoint (cond-> (assoc config :endpoint endpoint)
                               endpoint-region
                               (assoc :endpoint-region endpoint-region))]
    (doseq [current-config [config config-with-endpoint]]
      (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 current-config)
            file-key (str "integration-test-" (UUID/randomUUID))]
        (core/put-object s3-boundary file-key (io/file test-file-1-path))
        (core/delete-object s3-boundary file-key)
        (let [result (core/get-object s3-boundary file-key)]
          (testing "Attempt to get an object that's been deleted should return an error"
            (is (not (:success? result)))
            (is (= (get-in result [:error-details :Code])
                   "NoSuchKey"))))
        (let [result (core/delete-object s3-boundary (str (UUID/randomUUID)))]
          (testing "Deleting an object that does not exist should work just fine"
            (is (:success? result))))))))

(deftest ^:integration rename-get-file-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        endpoint-region (System/getenv "TEST_OBJECT_STORAGE_S3_REGION")
        config-with-endpoint (cond-> (assoc config :endpoint endpoint)
                               endpoint-region
                               (assoc :endpoint-region endpoint-region))]
    (doseq [current-config [config config-with-endpoint]]
      (let [s3-boundary (ig/init-key :dev.gethop.object-storage/s3 current-config)
            src-key (str "integration-test-" (UUID/randomUUID))
            dst-key (str "integration-test-" (UUID/randomUUID))
            put-result (core/put-object s3-boundary src-key (io/file test-file-1-path))]
        (testing "testing put-object"
          (is (:success? put-result)))
        (testing "Successful rename and get object"
          (let [rename-result (core/rename-object s3-boundary src-key dst-key)]
            (testing "testing rename-object"
              (is (:success? rename-result)))
            (testing "testing get-object on destination object"
              (let [get-result (core/get-object s3-boundary dst-key)]
                (is (:success? get-result))
                (is (= (digest/sha-256 (File. ^String test-file-1-path))
                       (digest/sha-256 (:object get-result))))))
            (testing "testing get-object on source object"
              (let [get-result (core/get-object s3-boundary src-key)]
                (is (not (:success? get-result)))
                (is (= "NoSuchKey" (-> get-result :error-details :Code)))))))
        (testing "Renaming file to itself also works (but does nothing)"
          (let [src-key dst-key
                rename-result (core/rename-object s3-boundary src-key dst-key)]
            (is (:success? rename-result))))
        (core/delete-object s3-boundary dst-key)))))

(deftest ^:integration list-test
  (let [endpoint (System/getenv "TEST_OBJECT_STORAGE_S3_ENDPOINT")
        endpoint-region (System/getenv "TEST_OBJECT_STORAGE_S3_REGION")
        config-with-endpoint (cond-> (assoc config :endpoint endpoint)
                               endpoint-region
                               (assoc :endpoint-region endpoint-region))]
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
        endpoint-region (System/getenv "TEST_OBJECT_STORAGE_S3_REGION")
        config-with-endpoint (cond-> (assoc config :endpoint endpoint)
                               endpoint-region
                               (assoc :endpoint-region endpoint-region))]
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

(defn- http-request
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
        endpoint-region (System/getenv "TEST_OBJECT_STORAGE_S3_REGION")
        config-with-endpoint (cond-> (assoc config :endpoint endpoint)
                               endpoint-region
                               (assoc :endpoint-region endpoint-region))]
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
            (is (URI. url))
            (is (= (digest/sha-256 f)
                   (digest/sha-256 (:body (http-request {:url url :method :get})))))))
        (testing "testing presigned url for :create method"
          (let [new-file-key (str file-key "-new")
                result (core/get-object-url s3-boundary new-file-key {:method :create})
                url (:object-url result)]
            (is (:success? result))
            (is (string? url))
            (is (URI. url))
            (is (not= :forbidden
                      (http-request {:url url
                                     :method :put
                                     :body (slurp f)})))
            (core/delete-object s3-boundary new-file-key)))
        (testing "testing :create presigned url, fails when used with :read method"
          (let [result (core/get-object-url s3-boundary file-key {:method :create})
                url (:object-url result)]
            (is (:success? result))
            (is (string? url))
            (is (URI. url))
            (is (= :forbidden
                   (http-request {:url url :method :get})))))
        (testing "testing :read presigned url with specific filename"
          (let [result (core/get-object-url s3-boundary file-key {:filename "asdfasdf.docx"})
                url (:object-url result)
                http-response (http-request {:url url :method :get})]
            (is (:success? result))
            (is (string? url))
            (is (URI. url))
            (is (= (#'s3/content-disposition-header "asdfasdf.docx" :attachment)
                   (get-in http-response [:headers :content-disposition])))))
        (core/delete-object s3-boundary file-key)))))
