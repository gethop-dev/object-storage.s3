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

(def ^:const default-presigned-url-lifespan
  "Default presigned urls lifespan, expressed in minutes"
  60)

(defn generate-presigned-url
  "Generates a url allowing access to the object without the need to auth oneself.
  See
  https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/AmazonS3Client.html#generatePresignedUrl-java.lang.String-java.lang.String-java.util.Date-com.amazonaws.HttpMethod-
  for keys that can be used in `opts`"
  [this obj-id opts]
  (->
   (assoc opts
          :bucket-name (:bucket-name this)
          :key obj-id
          :expiration (Date. (+ (System/currentTimeMillis)
                                (int (* (:presigned-url-lifespan this) 60 1000)))))
   (aws-s3/generate-presigned-url)
   .toString))

(defn- get-object*
  "Get the object with key `obj-id` from S3 bucket referenced by `this`, using `opts` options.
  See
  https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/GetObjectRequest.html
  for keys that can be used in `opts`"
  [this obj-id opts]
  (->
   (assoc opts
          :bucket-name (:bucket-name this)
          :key obj-id)
   (aws-s3/get-object)
   :input-stream))

(defn- put-object*
  "Put `object` in the S3 bucket referenced by `this`, using `obj-id` as the key.
  Use `opts` to specify additional put options.

  If you want to upload an object that is an input stream instead of a
  file, pass `nil` as `object` argument, and pass in the input stream
  as `:input-stream` key in `opts` argument. In that case, if you know
  the size of the content in the input-stream, make sure you also
  specify the `:metadata` key to specify the that size (see links
  below for specific format) for an example)

  See
  https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/PutObjectRequest.html
  and
  https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/AmazonS3Client.html#putObject-java.lang.String-java.lang.String-java.lang.String-
  for keys that can be used in `opts`"
  [this obj-id object opts]
  (let [opts (assoc opts
                    :bucket-name (:bucket-name this)
                    :key obj-id)
        opts (if (:input-stream opts)
               opts
               (assoc opts :file object))]
    (aws-s3/put-object opts)))

(defn- delete-object*
  "Delete the object `obj-id` from S3 bucket referenced by `this`"
  [this obj-id]
  (aws-s3/delete-object {:bucket-name (:bucket-name this), :key obj-id}))

(defrecord AWSS3Bucket [bucket-name presigned-url-lifespan]
  core/ObjectStorage
  (get-object [this obj-id]
    (get-object* this obj-id {}))
  (get-object [this obj-id opts]
    (get-object* this obj-id opts))

  (get-object-url [this obj-id]
    (generate-presigned-url this obj-id {}))
  (get-object-url [this obj-id opts]
    (generate-presigned-url this obj-id opts))

  (put-object [this obj-id object]
    (put-object* this obj-id object {}))
  (put-object [this obj-id object opts]
    (put-object* this obj-id object opts))

  (delete-object [this obj-id]
    (delete-object* this obj-id)))

(defmethod ig/init-key :magnet.object-storage/s3 [_ {:keys [bucket-name presigned-url-lifespan]
                                                     :or {presigned-url-lifespan default-presigned-url-lifespan}}]
  (map->AWSS3Bucket {:bucket-name bucket-name
                     :presigned-url-lifespan presigned-url-lifespan}))
