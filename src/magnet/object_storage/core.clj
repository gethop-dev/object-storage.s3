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
  (put-object [this object-id object] [this object-id object opts])
  (get-object [this object-id] [this object-id opts])
  (get-object-url [this object-id] [this object-id opts])
  (delete-object [this object-id] [this object-id opts]))
