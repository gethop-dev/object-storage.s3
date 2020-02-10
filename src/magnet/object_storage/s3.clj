;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns magnet.object-storage.s3
  (:require [amazonica.aws.s3 :as aws-s3]
            [amazonica.core :refer [ex->map]]
            [clojure.spec.alpha :as s]
            [integrant.core :as ig]
            [magnet.object-storage.core :as core])
  (:import [com.amazonaws.services.s3.model ResponseHeaderOverrides]
           [java.util Date]))

(def ^:const default-presigned-url-lifespan
  "Default presigned urls lifespan, expressed in minutes"
  60)

(defn- ex->result
  "Create a result map from `e` exception details"
  [e]
  (cond
    (instance? com.amazonaws.AmazonServiceException e)
    {:success? false
     :error-details (dissoc (ex->map e) :stack-trace)}

    true
    (let [error-details {:message (.getMessage e)}]
      {:success? false
       :error-details (cond-> error-details
                        (instance? com.amazonaws.AmazonClientException e)
                        (assoc :error-type "Client"))})))

(s/def ::AWSS3Bucket (s/keys :req-un [::bucket-name ::presigned-url-lifespan]))

(defn- put-object*
  "Put `object` in the S3 bucket referenced by `this`, using `object-id` as the key.
  Use `opts` to specify additional put options.

  `object` can be either a File object or an InputStream. In the
  latter case, if you know the size of the content in the InputStream,
  add the `:metadata` key to the `opts` map. Its value should be a map
  with a key called `:object-size`, with the size as its value."
  [this object-id object opts]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/object object)
              (s/valid? ::core/put-object-opts opts))]}
  (try
    (let [metadata (:metadata opts)
          encryption (:encryption opts)
          request {:bucket-name (:bucket-name this)
                   :key object-id
                   :file object}
          request (cond-> request
                    (instance? java.io.InputStream object)
                    (->
                     (dissoc :file)
                     (assoc :input-stream object)
                     (assoc-in [:metadata :content-length] (:object-size metadata)))

                    encryption
                    (assoc :encryption encryption))]
      ;; putObject either succeeds or throws an exception
      (aws-s3/put-object request)
      {:success? true})
    (catch Exception e
      (ex->result e))))

(s/fdef put-object*
  :args ::core/put-object-args
  :ret  ::core/put-object-ret)

(defn- get-object*
  "Get the object with key `object-id` from S3 bucket referenced by `this`, using `opts` options"
  [this object-id opts]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/get-object-opts opts))]}
  (try
    (let [encryption (:encryption opts)
          request {:bucket-name (:bucket-name this)
                   :key object-id}
          request (cond-> request
                    encryption
                    (assoc :encryption encryption))
          result (aws-s3/get-object request)]
      ;; getObject can return null in some cases. Quoting
      ;; documentation "When specifying constraints in the request
      ;; object, the client needs to be prepared to handle this method
      ;; returning null if the provided constraints aren't met when
      ;; Amazon S3 receives the request."
      (if result
        {:success? true
         :object (:input-stream result)}
        {:success? false
         :error-details {:error-code "RequestConstraintsNotMet"}}))
    (catch Exception e
      (ex->result e))))

(s/fdef get-object*
  :args ::core/get-object-args
  :ret  ::core/get-object-ret)

(defn- kw->http-method
  [k]
  {:pre [(s/valid? ::core/method k)]}
  (k {:create "PUT" :read "GET", :update "PUT", :delete "DELETE"}))

(defn- attachment-header
  [filename]
  (->
   (ResponseHeaderOverrides.)
   (.withContentDisposition (str "attachment; filename=" filename))))

(defn- get-object-url*
  "Generates a url allowing access to the object without the need to auth oneself.
  Get the object with key `object-id` from S3 bucket referenced by
  `this`, using `opts` options"
  [this object-id opts]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/get-object-url-opts opts))]}
  (try
    (let [expiration (Date. (+ (System/currentTimeMillis)
                               (int (* (:presigned-url-lifespan this) 60 1000))))
          method (:method opts)
          filename (:filename opts)
          request {:bucket-name (:bucket-name this)
                   :key object-id
                   :expiration expiration}
          request (cond-> request
                    method
                    (assoc :method (kw->http-method method))

                    filename
                    (assoc :response-headers (attachment-header filename)))
          presigned-url (->
                         request
                         (aws-s3/generate-presigned-url)
                         .toString)]
      ;; generatePresignedUrl either succeeds or throws an exception
      {:success? true
       :object-url presigned-url})
    (catch Exception e
      (ex->result e))))

(s/fdef get-object-url*
  :args ::core/get-object-url-args
  :ret  ::core/get-object-url-ret)

(defn- delete-object*
  "Delete the object `object-id` from S3 bucket referenced by `this`
  Use `opts` to specify additional delete options."
  [this object-id opts]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/delete-object-opts opts))]}
  (try
    ;; deleteObject either succeeds or throws an exception
    (aws-s3/delete-object {:bucket-name (:bucket-name this), :key object-id})
    {:success? true}
    (catch Exception e
      (ex->result e))))

(s/fdef delete-object*
  :args ::core/delete-object-args
  :ret  ::core/delete-object-ret)

(defn- list-objects*
  [this parent-object-id]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id parent-object-id))]}
  (try
    (let [result (aws-s3/list-objects-v2 {:bucket-name (:bucket-name this)
                                          :prefix (str parent-object-id)})]
      {:success? true
       :objects (->> result
                     :object-summaries
                     (map (fn [{:keys [key last-modified size]}]
                            {:object-id key
                             :last-modified last-modified
                             :size size})))})
    (catch Exception e
      (ex->result e))))

(s/fdef list-objects*
  :args ::core/list-objects-args
  :ret  ::core/list-objects-ret)

(defrecord AWSS3Bucket [bucket-name presigned-url-lifespan]
  core/ObjectStorage
  (put-object [this object-id object]
    (put-object* this object-id object {}))
  (put-object [this object-id object opts]
    (put-object* this object-id object opts))

  (get-object [this object-id]
    (get-object* this object-id {}))
  (get-object [this object-id opts]
    (get-object* this object-id opts))

  (get-object-url [this object-id]
    (get-object-url* this object-id {}))
  (get-object-url [this object-id opts]
    (get-object-url* this object-id opts))

  (delete-object [this object-id]
    (delete-object* this object-id {}))
  (delete-object [this object-id opts]
    (delete-object* this object-id opts))

  (list-objects [this parent-object-id]
    (list-objects* this parent-object-id)))

(defmethod ig/init-key :magnet.object-storage/s3 [_ {:keys [bucket-name presigned-url-lifespan]
                                                     :or {presigned-url-lifespan default-presigned-url-lifespan}}]
  (map->AWSS3Bucket {:bucket-name bucket-name
                     :presigned-url-lifespan presigned-url-lifespan}))
