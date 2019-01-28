;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns magnet.object-storage.s3
  (:require
   [amazonica.aws.s3 :as aws-s3]
   [integrant.core :as ig]
   [magnet.object-storage.core :as core])
  (:import
   [java.util Date]))

(def ^:const default-presigned-url-lifespan 60)

(defn generate-presigned-url
  "Generates a url allowing access to the object without the need to auth oneself.
  The default lifespan of the link is 1 hour however it could be configured by
  passing `lifespan` argument (measured in minutes)"
  [bucket-name object-key lifespan]
  (->
   (aws-s3/generate-presigned-url
    bucket-name
    object-key
    (Date. (+ (System/currentTimeMillis) (int (* lifespan 60 1000)))))
   .toString))

(defrecord AWSS3Bucket [bucket-name presigned-url-lifespan]
  core/ObjectStorage
  (get-object [this obj-id]
    (->
     (aws-s3/get-object
      (:bucket-name this)
      obj-id)
     :input-stream))
  (get-object-url [this obj-id]
    (generate-presigned-url (:bucket-name this) obj-id (:presigned-url-lifespan this)))
  (put-object [this obj-id object]
    (aws-s3/put-object
     (:bucket-name this)
     obj-id
     object))
  (delete-object [this obj-id]
    (aws-s3/delete-object
     {:bucket-name (:bucket-name this)
      :key obj-id})))

(defmethod ig/init-key :magnet.object-storage/s3 [_ {:keys [bucket-name presigned-url-lifespan]
                                                     :or {presigned-url-lifespan default-presigned-url-lifespan}}]
  (map->AWSS3Bucket {:bucket-name bucket-name
                     :presigned-url-lifespan presigned-url-lifespan}))
