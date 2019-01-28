;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/
(ns magnet.object-storage.core)

(defprotocol ObjectStorage
  "Abstraction for managing objects storage"
  (get-object [this obj-id])
  (get-object-url [this obj-id])
  (put-object [this obj-id object])
  (delete-object [this obj-id]))
