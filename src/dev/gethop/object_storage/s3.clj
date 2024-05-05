;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns dev.gethop.object-storage.s3
  (:require [amazonica.aws.s3 :as aws-s3]
            [amazonica.core :refer [ex->map]]
            [clojure.spec.alpha :as s]
            [dev.gethop.object-storage.core :as core]
            [integrant.core :as ig]
            [lambdaisland.uri :refer [map->query-string query-map uri]])
  (:import [com.amazonaws.services.s3.model ResponseHeaderOverrides]
           [java.net URL]
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

    :else
    (let [error-details {:message (.getMessage ^Exception e)}]
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
                    (assoc :encryption encryption)

                    (:explicit-object-acl this)
                    (assoc :access-control-list (:explicit-object-acl this)))]
      ;; putObject either succeeds or throws an exception
      (if-not (:endpoint this)
        (aws-s3/put-object request)
        (aws-s3/put-object {:endpoint (:endpoint this)} request))
      {:success? true})
    (catch Exception e
      (ex->result e))))

(s/fdef put-object*
  :args ::core/put-object-args
  :ret  ::core/put-object-ret)

(defn- copy-object*
  "Copy `source-object-id` object into `destination-object-id` object.

  For now there is no support to copy objects between different
  Buckets. Also encryption is only supported just as a side-effect of
  copying an already encrypted object. There is no support to change
  the original encryption or metadata for now.

  Use `opts` to specify additional options. Right now there is no one
  supported."
  [this source-object-id destination-object-id opts]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id source-object-id)
              (s/valid? ::core/object-id destination-object-id)
              (s/valid? ::core/copy-object-opts opts))]}
  (try
    (let [bucket-name (:bucket-name this)
          request {:source-bucket-name bucket-name
                   :destination-bucket-name bucket-name
                   :source-key source-object-id
                   :destination-key destination-object-id}
          request (cond-> request
                    (:explicit-object-acl this)
                    (assoc :access-control-list (:explicit-object-acl this)))]
      ;; copyObject either succeeds or throws an exception
      (if-not (:endpoint this)
        (aws-s3/copy-object request)
        (aws-s3/copy-object {:endpoint (:endpoint this)} request))
      {:success? true})
    (catch Exception e
      (ex->result e))))

(s/fdef copy-object*
  :args ::core/copy-object-args
  :ret  ::core/copy-object-ret)

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
          result (if-not (:endpoint this)
                   (aws-s3/get-object request)
                   (aws-s3/get-object {:endpoint (:endpoint this)} request))]
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
  [content-disposition content-type filename]
  (let [rho (ResponseHeaderOverrides.)
        cd (str (case content-disposition
                  :attachment "attachment"
                  :inline "inline")
                "; filename=" filename)]
    (-> rho
        (.withContentType content-type)
        (.withContentDisposition cd))))

(defn- presigned-url->public-url
  [presigned-url]
  (let [filtered-query-string (-> (query-map presigned-url {})
                                  (select-keys [:response-content-disposition :response-content-type])
                                  map->query-string)
        public-uri (assoc (uri presigned-url)
                          :fragment nil
                          :query filtered-query-string)]
    (str public-uri)))

(defn- get-object-url*
  "Generates a url allowing access to the object without the need to auth oneself.
  Uses the object with key `object-id` from S3 bucket referenced by
  `this`, using `opts` options."
  [this object-id opts]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/get-object-url-opts opts))]}
  (try
    (let [expiration (Date. (+ (System/currentTimeMillis)
                               (int (* (double (:presigned-url-lifespan this)) 60 1000))))
          method (:method opts)
          content-disposition (get opts :content-disposition :attachment)
          content-type (get opts :content-type "application/octet-stream")
          filename (:filename opts)
          object-public-url? (:object-public-url? opts)
          request {:bucket-name (:bucket-name this)
                   :key object-id
                   :expiration expiration}
          request (cond-> request
                    method
                    (assoc :method (kw->http-method method))

                    filename
                    (assoc :response-headers (attachment-header content-disposition
                                                                content-type
                                                                filename)))
          presigned-url (if-not (:endpoint this)
                          (aws-s3/generate-presigned-url request)
                          (aws-s3/generate-presigned-url {:endpoint (:endpoint this)}
                                                         request))]
      ;; generatePresignedUrl either succeeds or throws an exception
      {:success? true
       :object-url (if object-public-url?
                     (presigned-url->public-url (.toString ^URL presigned-url))
                     (.toString ^URL presigned-url))})
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

    (if-not (:endpoint this)
      (aws-s3/delete-object {:bucket-name (:bucket-name this), :key object-id})
      (aws-s3/delete-object {:endpoint (:endpoint this)}
                            {:bucket-name (:bucket-name this) :key object-id}))
    {:success? true}
    (catch Exception e
      (ex->result e))))

(s/fdef delete-object*
  :args ::core/delete-object-args
  :ret  ::core/delete-object-ret)

(defn- build-object-list
  "Build a list of all child objects for the given `parent-object-id`
  from S3 bucket reference by `this`. As an S3 bucket can contain a
  virtually unlimited number of objects, listObjectsV2 paginates the
  results. This function takes care of looping while there are still
  objects pending."
  [this parent-object-id]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id parent-object-id))]}
  (loop [object-list []
         partial-list (if-not (:endpoint this)
                        (aws-s3/list-objects-v2 {:bucket-name (:bucket-name this)
                                                 :prefix (str parent-object-id)})
                        (aws-s3/list-objects-v2 {:endpoint (:endpoint this)}
                                                {:bucket-name (:bucket-name this)
                                                 :prefix (str parent-object-id)}))]
    (if-not (:truncated? partial-list)
      (concat object-list (:object-summaries partial-list))
      (let [object-list (concat object-list (:object-summaries partial-list))
            continuation-token (:next-continuation-token partial-list)]
        (recur object-list
               (if-not (:endpoint this)
                 (aws-s3/list-objects-v2 {:bucket-name (:bucket-name this)
                                          :prefix (str parent-object-id)
                                          :continuation-token continuation-token})
                 (aws-s3/list-objects-v2 {:endpoint (:endpoint this)}
                                         {:bucket-name (:bucket-name this)
                                          :prefix (str parent-object-id)
                                          :continuation-token continuation-token})))))))

(defn- list-objects*
  "Lists all child objects for the given `parent-object-id` from S3
  bucket referenced by `this`."
  [this parent-object-id]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id parent-object-id))]}
  (try
    (let [result (build-object-list this parent-object-id)]
      {:success? true
       :objects (pmap (fn [{:keys [key last-modified size]}]
                        {:object-id key
                         :last-modified last-modified
                         :size size})
                      result)})
    (catch Exception e
      (ex->result e))))

(s/fdef list-objects*
  :args ::core/list-objects-args
  :ret  ::core/list-objects-ret)

(defrecord AWSS3Bucket [bucket-name presigned-url-lifespan endpoint explicit-object-acl]
  core/ObjectStorage
  (put-object [this object-id object]
    (put-object* this object-id object {}))
  (put-object [this object-id object opts]
    (put-object* this object-id object opts))

  (copy-object [this source-object-id destination-object-id]
    (copy-object* this source-object-id destination-object-id {}))
  (copy-object [this source-object-id destination-object-id opts]
    (copy-object* this source-object-id destination-object-id opts))

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
    (list-objects* this parent-object-id))
  (list-objects [this parent-object-id _opts]
    (list-objects* this parent-object-id)))

(defmethod ig/init-key :dev.gethop.object-storage/s3 [_ {:keys [bucket-name presigned-url-lifespan
                                                                endpoint explicit-object-acl]
                                                         :or {presigned-url-lifespan default-presigned-url-lifespan
                                                              endpoint nil
                                                              explicit-object-acl nil}}]
  (map->AWSS3Bucket {:bucket-name bucket-name
                     :endpoint endpoint
                     :explicit-object-acl explicit-object-acl
                     :presigned-url-lifespan presigned-url-lifespan}))
