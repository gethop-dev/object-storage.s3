;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/

(ns magnet.object-storage.core
  (:require [clojure.spec.alpha :as s]))

;; Specs used to validate arguments and return values for
;; implementations of the protocol
(s/def ::object-id (s/or :string string? :uuid uuid?))
(s/def ::encryption map?)
(s/def ::object (s/or :file #(instance? java.io.File %)
                      :input-stream #(instance? java.io.InputStream %)))
(s/def ::success? boolean?)
(s/def ::error-details map?)
(s/def ::url (s/or :string string? :url #(instance? java.net.URL %)))
(s/def ::method #{:create :read :update :delete})
(s/def ::filename string?)
(s/def ::object-size (s/and integer? #(>= % 0)))
(s/def ::metadata (s/keys :req-un [::object-size]))

(s/def ::put-object-opts (s/keys :opt-un [::encryption ::metadata]))
(s/def ::put-object-args (s/cat :config record? :object-id ::object-id :object ::object :opts ::put-object-opts))
(s/def ::put-object-ret (s/keys :req-un [::success?]
                                :opt-un [::error-details]))

(s/def ::get-object-opts (s/keys :opt-un [::encryption]))
(s/def ::get-object-args (s/cat :config record? :object-id ::object-id :opts (s/? ::get-object-opts)))
(s/def ::get-object-ret (s/keys :req-un [::success? (or ::object ::error-details)]))

(s/def ::get-object-url-opts (s/keys :opt-un [::method ::filename]))
(s/def ::get-object-url-args (s/cat :config record? :object-id ::object-id :opts (s/? ::get-object-url-opts)))
(s/def ::get-object-url-ret (s/keys :req-un [::success? (or ::object-url ::error-details)]))

(s/def ::delete-object-opts map?)
(s/def ::delete-object-args (s/cat :config record? :object-id ::object-id :opts (s/? ::delete-object-opts)))
(s/def ::delete-object-ret (s/keys :req-un [::success?]
                                   :opt-un [::error-details]))

(defprotocol ObjectStorage
  "Abstraction for managing objects storage"
  (put-object
    [this object-id object]
    [this object-id object opts]
    "Put `object` in the storage system, using `object-id` as the key.
  Use `opts` to specify additional put options.

  `object` can be either a File object or an InputStream. In the
  latter case, if you know the size of the content in the InputStream,
  add the `:metadata` key to the `opts` map. Its value should be a map
  with a key called `:object-size`, with the size as its value.")
  (get-object
    [this object-id]
    [this object-id opts]
    "Get the object with key `object-id` from the storage system, using `opts` options")
  (get-object-url
    [this object-id]
    [this object-id opts]
    "Generates a url allowing access to the object without the need to auth oneself.
  Get the object with key `object-id` from the storage system, using `opts` options")
  (delete-object
    [this object-id]
    [this object-id opts]
    "Delete the object `object-id` from the storage system
  Use `opts` to specify additional delete options."))
