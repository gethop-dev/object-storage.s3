# Duct Object Storage

A [Duct](https://github.com/duct-framework/duct) library that provides [Integrant](https://github.com/weavejester/integrant) keys for managing AWS S3 objects.

## Usage

This library provides a single Integrant key, `magnet.object-storage/s3`, that expects the following keys:

* `:bucket-name`: The name of the bucket where you want to perform S3 object level operations.
* `:presigned-url-lifespan`: Lifespan for the presigned urls. It has to be specified in minutes, and the default value is one hour.

Example usage:

``` edn
 :magnet.aws/s3 {:bucket-name "hydrogen-test"
                 :presigned-url-lifespan  2.5 }
```
Key initialization returns an `AWS3Bucket` record that can be used to perform the following operations:

* `get-object [record object-id]` Retrieves the specified object from S3.
* `get-object-url [record object-id]` Returns a presigned url that can be used to access the specified object without authentication. The url lifespan can be specified in the record initialization.
* `put-object [record object-id object]` Uploads an object to S3 with the specified object-id.
* `delete-object [record object-id]` Deletes the specified object from S3.

## License

Copyright (c) Magnet S Coop 2018.

The source code for the library is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
