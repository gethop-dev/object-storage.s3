;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns dev.gethop.object-storage.s3
  (:require [aws-simple-sign.core :as aws-sign]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [cognitect.aws.client.api :as aws]
            [dev.gethop.object-storage.core :as core]
            [integrant.core :as ig]
            [lambdaisland.uri :refer [uri]]
            [ring.util.codec :as codec])
  (:import (java.net URL
                     URI)
           (java.util Date)))

(def ^:const default-presigned-url-lifespan
  "Default presigned urls lifespan, expressed in minutes"
  60)

(def ^:private metadata-keys->content-headers
  {:content-type :ContentType
   :content-disposition :ContentDisposition
   :content-encoding :ContentEncoding
   :content-length :ContentLength})

(def ^:private metadata-keys
  (into [] (keys metadata-keys->content-headers)))

(def ^:private supported-metadata
  (into metadata-keys
        [:filename]))

(def ^:private content-headers->metadata-keys
  (set/map-invert metadata-keys->content-headers))

(def ^:private content-headers
  (into [] (keys content-headers->metadata-keys)))

(defn- anomaly->result
  "Create a result map from a Cognitect anomaly details"
  [anomaly]
  (cond
    (:Error anomaly)
    {:success? false
     :error-details (:Error anomaly)}

    :else
    {:success? false
     :error-details anomaly}))

(s/def ::bucket-name (s/and string? #(> (count %) 0)))
(s/def ::presigned-url-lifespan number?)
(s/def ::endpoint (s/nilable (s/or :string-url (s/and string?
                                                      (fn [s]
                                                        (try
                                                          (URI. s)
                                                          (catch Throwable _
                                                            false))))
                                   :url #(instance? URL %))))
(s/def ::endpoint-region (s/nilable string?))
(s/def ::explicit-object-acl (s/nilable string?))
(s/def ::AWSS3Bucket (s/keys :req-un [::bucket-name ::presigned-url-lifespan]
                             :opt-un [::endpoint ::endpoint-region ::explicit-object-acl]))

(defn- content-disposition-header
  [filename content-disposition-type]
  (str
   (case content-disposition-type
     :attachment "attachment"
     :inline "inline")
   "; filename*=UTF-8''" (codec/percent-encode filename "UTF-8")))

(defn- object-key->filename
  "Try to infer a filename from the object key.

  The `/` character is the delimiter[1] separating the \"prefix\"es
  and the \"object\" itself (which is the last component of the object
  key, if it has any prefixes).

  Things can become quite complicated if object-key contains the `.`
  or `..` prefixes, or consecutive `/` characters, of when the
  object-key ends in a trailing `/`.  But sane object-keys shouldn't
  contain use cases. So we apply a naive strategy.

  [1] See https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-keys.html"
  [object-key]
  (peek (str/split object-key #"/+")))

(defn- put-object*
  "Put `object` in the S3 bucket referenced by `this`, using `object-id` as the key.
  Use `opts` to specify additional put options.

  `object` can be either a File object or an InputStream. In the
  latter case, if you know the size of the content in the InputStream,
  add the `:metadata` key to the `opts` map. Its value should be a map
  with a key called `:object-size`, with the size as its value.

  You can also specify the object content-type, and the desired
  content-disposition and/or content-encoding for the later GET
  operations that use the object URL directly (only makes sense for
  public buckets). For that you can pass the `:content-type`,
  `:content-disposition` and `:content-encoding` keys in the
  `:metadata` map."
  [this object-id object opts]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/object object)
              (s/valid? ::core/put-object-opts opts))]}
  (let [metadata (:metadata opts)
        filename (or (:filename metadata)
                     (object-key->filename object-id))
        update-fn (fnil (partial content-disposition-header filename)
                        ;; "inline" content-disposition is the default so
                        ;; setting it when not passed in as an argument is
                        ;; idempotent, but simplifies things.
                        :inline)
        content-headers (-> metadata
                            (select-keys supported-metadata)
                            (update :content-disposition update-fn)
                            (set/rename-keys {:object-size :content-length})
                            (set/rename-keys metadata-keys->content-headers))
        request (into content-headers {:Bucket (:bucket-name this)
                                       :Key object-id})
        request (cond-> request
                  (instance? java.io.File object)
                  (->
                   (assoc :Body (io/input-stream object))
                   (assoc :ContentLength (.length ^java.io.File object)))

                  (instance? java.io.InputStream object)
                  (->
                   (assoc :Body object)
                   (cond->
                    (:object-size metadata)
                     (assoc :ContentLength (:object-size metadata))))

                  (:explicit-object-acl this)
                  (assoc :ACL (:explicit-object-acl this)))
        response (aws/invoke (:client this)
                             {:op :PutObject
                              :request request})]
    (if-not (:cognitect.anomalies/category response)
      {:success? true}
      (anomaly->result response))))

(s/fdef put-object*
  :args ::core/put-object-args
  :ret  ::core/put-object-ret)

(defn- copy-object*
  "Copy `source-object-id` object into `destination-object-id` object.

  For now there is no support to copy objects between different
  Buckets. There is also no support to change the original metadata
  for now.

  Use `opts` to specify additional options. Right now there is none
  supported."
  [this source-object-id destination-object-id opts]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id source-object-id)
              (s/valid? ::core/object-id destination-object-id)
              (s/valid? ::core/copy-object-opts opts))]}
  (if (= source-object-id destination-object-id)
    ;; Copying object to itself. No need to do anything.
    {:success? true}
    (let [bucket-name (:bucket-name this)
          request {:CopySource (str bucket-name "/" source-object-id)
                   :Bucket bucket-name
                   :Key destination-object-id}
          request (cond-> request
                    (:explicit-object-acl this)
                    (assoc :ACL (:explicit-object-acl this)))
          response (aws/invoke (:client this)
                               {:op :CopyObject
                                :request request})]
      (if-not (:cognitect.anomalies/category response)
        {:success? true}
        (anomaly->result response)))))

(s/fdef copy-object*
  :args ::core/copy-object-args
  :ret  ::core/copy-object-ret)

(defn- get-object*
  "Get the object with key `object-id` from S3 bucket referenced by `this`, using `opts` options"
  [this object-id opts]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/get-object-opts opts))]}
  (let [request {:Bucket (:bucket-name this)
                 :Key object-id}
        response (aws/invoke (:client this)
                             {:op :GetObject
                              :request request})]
    (if (:cognitect.anomalies/category response)
      (anomaly->result response)
      {:success? true
       :object (:Body response)
       :metadata (-> response
                     (select-keys content-headers)
                     (set/rename-keys content-headers->metadata-keys)
                     (set/rename-keys {:content-length :object-size}))})))

(s/fdef get-object*
  :args ::core/get-object-args
  :ret  ::core/get-object-ret)

(defn- kw->http-method
  [k]
  {:pre [(s/valid? ::core/method k)]}
  (k {:create "PUT" :read "GET", :update "PUT", :delete "DELETE"}))

(defn- presigned-url->public-url
  [presigned-url]
  (str (assoc (uri presigned-url)
              :fragment nil
              :query nil)))

(defn- get-object-url*
  "Generates a url allowing access to the object without the need to auth oneself.
  Uses the object with key `object-id` from S3 bucket referenced by
  `this`, using `opts` options."
  [this object-id opts]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/get-object-url-opts opts))]}
  (let [ref-time (Date.)
        expires (int (* (double (:presigned-url-lifespan this)) 60))
        method (:method opts)
        content-disposition (get opts :content-disposition :attachment)
        content-type (get opts :content-type "application/octet-stream")
        filename (:filename opts)
        object-public-url? (:object-public-url? opts)
        opts (cond-> {:ref-time ref-time
                      :expires (str expires)}
               method
               (assoc :method (kw->http-method method))

               filename
               (assoc :override-response-headers
                      {:response-content-disposition (content-disposition-header filename content-disposition)
                       :response-content-type content-type}))
        presigned-url (aws-sign/generate-presigned-url (:client this)
                                                       (:bucket-name this)
                                                       object-id
                                                       opts)]
    {:success? true
     :object-url (if object-public-url?
                   (presigned-url->public-url presigned-url)
                   presigned-url)}))

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
  (let [request {:Bucket (:bucket-name this)
                 :Key object-id}
        response (aws/invoke (:client this)
                             {:op :DeleteObject
                              :request request})]
    (if (:cognitect.anomalies/category response)
      (anomaly->result response)
      {:success? true})))

(s/fdef delete-object*
  :args ::core/delete-object-args
  :ret  ::core/delete-object-ret)

(defn- rename-object*
  "Rename the object `object-id` from S3 bucket referenced by `this`, to new-object-id`"
  [this object-id new-object-id]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id object-id)
              (s/valid? ::core/object-id new-object-id))]}
  ;; S3 does not have a rename operation. The recommended way it to
  ;; copy the object key to the new key, and then delete the
  ;; original key.
  (if (= object-id new-object-id)
    ;; Renaming object to itself. No need to do anything.
    {:success? true}
    (let [result (copy-object* this object-id new-object-id {})]
      (if-not (:success? result)
        result
        (delete-object* this object-id {})))))

(s/fdef rename-object*
  :args ::core/rename-object-args
  :ret  ::core/rename-object-ret)

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
         partial-list (aws/invoke (:client this)
                                  {:op :ListObjectsV2
                                   :request {:Bucket (:bucket-name this)
                                             :Prefix (str parent-object-id)}})]
    (if (:cognitect.anomalies/category partial-list)
      (throw (ex-info "ListObjectV2 error" partial-list))
      (if-not (:IsTruncated partial-list)
        (concat object-list (:Contents partial-list))
        (let [object-list (concat object-list (:Contents partial-list))
              continuation-token (:NextContinuationToken partial-list)]
          (recur object-list
                 (aws/invoke (:client this)
                             {:op :ListObjectsV2
                              :request {:Bucket (:bucket-name this)
                                        :Prefix (str parent-object-id)
                                        :ContinuationToken continuation-token}})))))))

(defn- list-objects*
  "Lists all child objects for the given `parent-object-id` from S3
  bucket referenced by `this`."
  [this parent-object-id]
  {:pre [(and (s/valid? ::AWSS3Bucket this)
              (s/valid? ::core/object-id parent-object-id))]}
  (try
    (let [result (build-object-list this parent-object-id)]
      {:success? true
       :objects (map (fn [{:keys [Key LastModified Size]}]
                       {:object-id Key
                        :last-modified LastModified
                        :size Size})
                     result)})
    (catch Exception e
      (anomaly->result (ex-data e)))))

(s/fdef list-objects*
  :args ::core/list-objects-args
  :ret  ::core/list-objects-ret)

(defrecord AWSS3Bucket [client bucket-name bucket-owner
                        presigned-url-lifespan explicit-object-acl]
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

  (rename-object [this object-id new-object-id]
    (rename-object* this object-id new-object-id))

  (list-objects [this parent-object-id]
    (list-objects* this parent-object-id))
  (list-objects [this parent-object-id _opts]
    (list-objects* this parent-object-id)))

(defn init-record
  "Returns an AWSS3Bucket record with the provided `config`"
  [{:keys [bucket-name presigned-url-lifespan
           endpoint endpoint-region explicit-object-acl]
    :or {presigned-url-lifespan default-presigned-url-lifespan
         endpoint nil
         endpoint-region nil
         explicit-object-acl nil}
    :as _config}]
  (let [{:keys [scheme host port]} (uri endpoint)
        client (aws/client (cond-> {:api :s3}
                             ;; Even if we have a different region for the endpoint, the
                             ;; default region provider in the library expects a valid
                             ;; *AWS* region name. Fake it for now (but only if we are
                             ;; using a custom endpoint), until we find a way to put the
                             ;; right region here. See
                             ;; https://github.com/cognitect-labs/aws-api/issues/150
                             endpoint-region (assoc :region "eu-west-1")
                             scheme (assoc-in [:endpoint-override :scheme] (keyword scheme))
                             host (assoc-in [:endpoint-override :hostname] host)
                             port (assoc-in [:endpoint-override :port] port)
                             endpoint-region (assoc-in [:endpoint-override :region] endpoint-region)))
        bucket-owner (when explicit-object-acl
                       (let [result (aws/invoke client {:op :GetBucketAcl :request {:Bucket bucket-name}})]
                         (when-not (:cognitect.anomalies/category result)
                           (-> result (:Owner) (:ID)))))]
    (map->AWSS3Bucket {:client client
                       :bucket-name bucket-name
                       :buclet-owner bucket-owner
                       :presigned-url-lifespan presigned-url-lifespan
                       :explicit-object-acl explicit-object-acl})))

(defmethod ig/init-key :dev.gethop.object-storage/s3
  [_ config]
  (init-record config))
