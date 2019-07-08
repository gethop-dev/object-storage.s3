[![Build Status](https://travis-ci.org/magnetcoop/object-storage.s3.svg?branch=master)](https://travis-ci.org/magnetcoop/object-storage.s3)
# Duct Object Storage

A [Duct](https://github.com/duct-framework/duct) library that provides [Integrant](https://github.com/weavejester/integrant) keys for managing AWS S3 objects.

## Installation

[![Clojars Project](https://clojars.org/magnet/object-storage.s3/latest-version.svg)](https://clojars.org/magnet/object-storage.s3)

## Usage

### Getting an `AWS3Bucket` record
This library provides a single Integrant key, `magnet.object-storage/s3`, that returns an `AWS3Bucket` record that can be used to perform S3 object operations. The key initialization expects the following keys:

* `:bucket-name`: The name of the bucket where you want to perform S3 operations.
* `:presigned-url-lifespan`: Lifespan for the presigned urls. It has to be specified in hours, and the default value is one.

Example usage:

``` edn
 :magnet.object-storage/s3 {:bucket-name "hydrogen-test"
                            :presigned-url-lifespan  2.5 }
```
### Performing S3 object operations
The `AWS3Bucket`record can be used to perform the following operations:

#### `get-object [record object-id opts]`
* description: Retrieves the specified object from S3.
* parameters: 
  - An `AWS3Bucket` record.
  - The object id you want to retrieve.
  - [Optional] A map with extra parameters that will be passed to the underlying [Amazonica S3 library](https://github.com/mcohen01/amazonica). See the [javadoc](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/AmazonS3Client.html#generatePresignedUrl-java.lang.String-java.lang.String-java.util.Date-com.amazonaws.HttpMethod-) for the keys that can be used here.
* returning value:
  - An `input-stream` of the desired object.
* Some examples:
```clj
user> (core/get-object s3-record "test.txt")
#object[com.amazonaws.services.s3.model.S3ObjectInputStream
0x7e8ac80b "com.amazonaws.services.s3.model.S3ObjectInputStream@7e8ac80b"]

user> (core/get-object s3-record "/test/foo.png" {:encryption {:key-pair key-pair}})
#object[com.amazonaws.services.s3.model.S3ObjectInputStream
0x3dc8baaa "com.amazonaws.services.s3.model.S3ObjectInputStream@3dc8baaa"] 
```
#### `put-object [record object-id object opts]`
* description: Uploads an object to S3 with the specified object-id.
* parameters: 
  - An `AWS3Bucket` record.
  - The object id.
  - The file you want to upload.
  - [Optional] A map with extra parameters that will be passed to the underlying [Amazonica S3 library](https://github.com/mcohen01/amazonica). See [this](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/PutObjectRequest.html) and [this](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/AmazonS3Client.html#putObject-java.lang.String-java.lang.String-java.lang.String-) for more information.
* returning value:
  - A map with some information about the object (see example below)
* Some examples:
```clj
user> (core/put-object s3-record "/test/foo.png"
{:metadata {:content-disposition nil, :expiration-time-rule-id nil, :user-metadata nil, 
:instance-length 0, :version-id nil, :server-side-encryption nil, 
:server-side-encryption-aws-kms-key-id nil, :etag "35111a831dc6ee8b75f33586cfd70c4e", 
:last-modified nil, :cache-control nil, :http-expires-date nil, :content-length 0, 
:content-type nil, :restore-expiration-time nil, :content-encoding nil, 
:expiration-time nil, :content-md5 nil, :ongoing-restore nil}, 
:etag "35111a831dc6ee8b75f33586cfd70c4e", :requester-charged? false, 
:content-md5 "NREagx3G7ot18zWGz9cMTg=="}

user> (core/get-object s3-record "/test/foo.png" {:encryption {:key-pair key-pair}})
``` 
If you want to upload an object that is an input stream instead, pass `nil` as `object` argument, and pass the input stream as `:input-stream` key in `opts` argument. In that case, if you know the size of the content in the input-stream, make sure you also specify it inside `:metadata` map with the `:content-length` key.
```clj
user> (core/put-object s3-record "test.txt" nil {:input-stream (input-stream (.getBytes "Test"))
                                                :metadata {:content-length 4}})
                                                
{:metadata {:content-disposition nil, :expiration-time-rule-id nil, :user-metadata nil, 
:instance-length 0, :version-id nil, :server-side-encryption nil,
:server-side-encryption-aws-kms-key-id nil, :etag "0cbc6611f5540bd0809a388dc95a615b",
:last-modified nil, :cache-control nil, :http-expires-date nil, :content-length 0, 
:content-type nil, :restore-expiration-time nil, :content-encoding nil, 
:expiration-time nil, :content-md5 nil, :ongoing-restore nil}, 
:etag "0cbc6611f5540bd0809a388dc95a615b", :requester-charged? false,
:content-md5 "DLxmEfVUC9CAmjiNyVphWw=="}
``` 
#### `delete-object [record object-id opts]`
* description: Deletes the specified object from S3.
* parameters: 
  - An `AWS3Bucket` record.
  - The object id you want to delete.
  - [Optional] A map with extra parameters that will be passed to the underlying [Amazonica S3 library](https://github.com/mcohen01/amazonica). See the [javadoc](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/DeleteObjectRequest.html) for the keys that can be used here.
* returning value:
  - nil
* Some examples:
```clj
user> (core/delete-object s3-record "/test/foo.png")
nil
```

#### `get-object-url [record object-id opts]`
* description: Gets a presigned url that can be used to access the specified object without authentication. The url lifespan can be specified in the record initialization.
* parameters: 
  - An `AWS3Bucket` record.
  - The object id.
  - [Optional] A map with extra parameters that will be passed to the underlying [Amazonica S3 library](https://github.com/mcohen01/amazonica). See the [javadoc](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/AmazonS3Client.html#generatePresignedUrl-java.lang.String-java.lang\
.String-java.util.Date-com.amazonaws.HttpMethod-) for the keys that can be used here.
* returning value:
  - Returns a presigned url that can be used to access the specified object without authentication
* Some examples:
```clj
user> (core/get-object-url s3-record "/test/foo.png")
"https://hydrogen-test.s3.eu-west-1.amazonaws.com/b?X-Amz-Algorithm=
AWS4-HMAC-SHA256&X-Amz-Date=20190708T141105Z&X-Amz-SignedHeaders=
host&X-Amz-Expires=3599&X-Amz-Credential=AKIAIDDFKQWZPM5QD2RA%2F2
0190708%2Feu-west-1%2Fs3%2Faws4_request&X-Amz-Signature=d34620a62
4dfec7ef3b943e1bec1d80e41bf97d3b391dc26a15aaf885e1f08b4"

user> (core/get-object-url s3-record "/test/foo.png" {:method "DELETE"})
"https://hydrogen-test.s3.eu-west-1.amazonaws.com/b?X-Amz-Algorithm=
AWS4-HMAC-SHA256&X-Amz-Date=20190708T141151Z&X-Amz-SignedHeaders=
host&X-Amz-Expires=3599&X-Amz-Credential=AKIAIDAGKQWZPM5QD2RA%2F201907
08%2Feu-west-1%2Fs3%2Faws4_request&X-Amz-Signature=803cf5f2eb6b53f4588
6896121ce83695df47d5f3ea274617a73cdec3090a0e6"
```

## License

Copyright (c) Magnet S Coop 2019.

The source code for the library is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
