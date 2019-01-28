;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns magnet.object-storage.s3-test
  (:require [clojure.test :refer :all]
            [integrant.core :as ig]
            [magnet.object-storage.s3]
            [magnet.object-storage.core :as core]
            [digest]
            [clojure.java.io :refer [delete-file]])
  (:import [java.util UUID]
           [com.amazonaws.services.s3.model AmazonS3Exception]
           [java.io File]
           [magnet.object_storage.s3 AWSS3Bucket]))

(def test-file-1-path "test-file-1")
(def test-file-2-path "test-file-2")

(defn setup []
  (spit test-file-1-path {:hello :world})
  (spit test-file-2-path [:apples :bananas]))

(defn teardown []
  (delete-file "test-file-1")
  (delete-file "test-file-2"))

(defn with-test-files [f]
  (setup)
  (f)
  (teardown))

(use-fixtures :each with-test-files)

(deftest protocol-test
  (let [config {:bucket-name "hydrogen-test"}
        s3-boundary (ig/init-key :magnet.object-storage/s3 config)]
    (is
     (= (class s3-boundary)
        AWSS3Bucket))))

(deftest ^:integration s3-test
  (let [config {:bucket-name "hydrogen-test"}
        s3-boundary (ig/init-key :magnet.object-storage/s3 config)
        new-file-key (str "integration-test-" (UUID/randomUUID))]
    (is
     (core/put-object s3-boundary new-file-key (File. test-file-1-path)))
    (is
     (core/get-object s3-boundary new-file-key))

    (core/delete-object s3-boundary new-file-key)               ;; just clean up after file creation

    #_(is
       (core/delete-object s3-boundary new-file-key))           ;; TODO amazonica always returns nil, no matter if a file was deleted or not.
    (is
     (thrown? AmazonS3Exception
              (core/get-object s3-boundary new-file-key))
     "Attempt to get an object that's been deleted should throw an exception.")
    (is
     (nil? (core/delete-object s3-boundary (str (UUID/randomUUID))))
     "Amazonica is expected to allow deletion of a file that doesn't exist.")

    (let [another-file-key (str "integration-test-" (UUID/randomUUID))]

      (is
       (let [file-upload-result (core/put-object s3-boundary another-file-key (File. test-file-1-path))
             file-2-upload-result (core/put-object s3-boundary another-file-key (File. test-file-2-path))]
         (= (digest/sha-256 (File. test-file-2-path))
            (digest/sha-256 (core/get-object s3-boundary another-file-key))))
       "It should be possible to replace an object."))))
