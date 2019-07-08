[![Build Status](https://travis-ci.org/magnetcoop/object-storage.s3.svg?branch=master)](https://travis-ci.org/magnetcoop/object-storage.s3)
# Duct Object Storage

A [Duct](https://github.com/duct-framework/duct) library that provides [Integrant](https://github.com/weavejester/integrant) keys for managing AWS S3 objects, implementing the `ObjectStorage` protocol.

## Installation

[![Clojars Project](https://clojars.org/magnet/object-storage.s3/latest-version.svg)](https://clojars.org/magnet/object-storage.s3)

## Usage

### Getting an `AWSS3Bucket` record

This library provides a single Integrant key, `:magnet.object-storage/s3`, that returns an `AWSS3Bucket` record that can be used to perform `ObjectStorage` protocol operations on a given S3 bucket. For the `get-object-url` operation it generates a presigned URL that can be used to access objects in private buckets without holding any AWS credentials, with a limited life span. The key initialization expects the following keys:

* `:bucket-name`: The name of the bucket where you want to perform S3 operations.
* `:presigned-url-lifespan`: Lifespan for the presigned URLs. It is specified in minutes (can use fractional values), and the default value is one hour.

Example configuration, with a presigned URL life span of 30 minutes:

``` edn
 :magnet.object-storage/s3 {:bucket-name "hydrogen-test"
                            :presigned-url-lifespan 30}
```

### Performing S3 object operations
You need to require the `magnet.object-storage.core` namespace to get the `ObjectStorage` protocol definition. Once you have the protocol in place, you can use the `AWSS3Bucket` record can be used to perform the following operations:

#### `(get-object [record object-id])`

* description: Retrieves the specified object from S3.
* parameters: 
  - `record`: An `AWSS3Bucket` record.
  - `object-id`: The key of the object in the S3 bucket that you want to retrieve.
* return value:
  - An `InputStream`-compatible stream, on the desired object. Note that the `InputStream` returned by `get-object` should be closed (.e.g, e.g. via slurp) or the HTTP connection pool will be exhausted after several objects are retrieved.

Example:

```clj
user> (require '[magnet.object-storage.core :as core])
nil
user> (core/get-object s3-record "test.txt")
#object[com.amazonaws.services.s3.model.S3ObjectInputStream
0x7e8ac80b "com.amazonaws.services.s3.model.S3ObjectInputStream@7e8ac80b"]
```

#### `(get-object [record object-id opts])`

* description: Retrieves the specified object from S3.
* parameters: 
  - `record`: An `AWSS3Bucket` record.
  - `object-id`: The key of the object in the S3 bucket that you want to retrieve.
  - `opts`: A map with extra parameters that will be passed to the underlying [Amazonica S3 library](https://github.com/mcohen01/amazonica). See the [javadoc](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/GetObjectRequest.html) for the keys that can be used here.
* return value:
  - An `InputStream`-compatible stream, on the desired object. Note that the `InputStream` returned by `get-object` should be closed (.e.g, e.g. via slurp) or the HTTP connection pool will be exhausted after several objects are retrieved.,

Example, retrieving a specific version of the object:

```clj
user> (core/get-object s3-record "/test/foo.png" {:version-id "L4kqtJlcpXroDTDmpUMLUo"})
#object[com.amazonaws.services.s3.model.S3ObjectInputStream
0x3dc8baaa "com.amazonaws.services.s3.model.S3ObjectInputStream@3dc8baaa"] 
```

#### `(put-object [record object-id object])`

* description: Uploads an object to S3 with `oject-id` as its key.
* parameters: 
  - `record`: An `AWSS3Bucket` record.
  - `object-id`: The key for the object in the S3 bucket that you want to upload.
  - `object`: The file you want to upload (as a `clojure.java.io/File`-compatible value).
* return value:
  - A map with some information about the object (see example below)

Example:
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
``` 

#### `(put-object [record object-id object opts])`

* description: Uploads an object to S3 with `oject-id` as its key.
* parameters: 
  - `record`: An `AWSS3Bucket` record.
  - `object-id`: The key for the object in the S3 bucket that you want to upload.
  - `object`: The file you want to upload (as a `clojure.java.io/File`-compatible value).
  - [Optional] A map with extra parameters that will be passed to the underlying [Amazonica S3 library](https://github.com/mcohen01/amazonica). See [PutObjectRequest class documentation](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/PutObjectRequest.html) and [AmazonS3Client class PutObject method documentation](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/AmazonS3Client.html#putObject-java.lang.String-java.lang.String-java.lang.String-) for more information.
* return value:
  - A map with some information about the object (see example below)

* Example: if you want to upload an object that is an input stream instead of a `File`, pass `nil` as `object` argument, and pass the input stream as `:input-stream` key in `opts` argument. In that case, if you know the size of the content in the InputStream, make sure you also specify it inside `:metadata` map with the `:content-length` key.

```clj
user> (require '[clojure.java.io :refer [input-stream]])
user> (core/put-object s3-record "test.txt" nil {:input-stream (input-stream (.getBytes "Test"))
                                                 :metadata {:content-length (.length "Test}})
{:metadata {:content-disposition nil, :expiration-time-rule-id nil, :user-metadata nil, 
:instance-length 0, :version-id nil, :server-side-encryption nil,
:server-side-encryption-aws-kms-key-id nil, :etag "0cbc6611f5540bd0809a388dc95a615b",
:last-modified nil, :cache-control nil, :http-expires-date nil, :content-length 0, 
:content-type nil, :restore-expiration-time nil, :content-encoding nil, 
:expiration-time nil, :content-md5 nil, :ongoing-restore nil}, 
:etag "0cbc6611f5540bd0809a388dc95a615b", :requester-charged? false,
:content-md5 "DLxmEfVUC9CAmjiNyVphWw=="}
``` 

#### `delete-object [record object-id]`

* description: Deletes the object with key `object-id` from S3.
* parameters: 
  - `record`: An `AWSS3Bucket` record.
  - `object-id`: The key for the object in the S3 bucket that you want to delete.
* return value:
  - nil

* Example:
```clj
user> (core/delete-object s3-record "/test/foo.png")
nil
```

#### `get-object-url [record object-id opts]`

* description: Gets a presigned URL that can be used to get the specified object without authentication. The URL lifespan is specified in the record initialization.
* parameters:
  - `record`: An `AWSS3Bucket` record.
  - `object-id`: The key for the object in the S3 bucket that you want to access without authentication.
* return value:
  - Returns a presigned URL that can be used to get the specified object without authentication

* Example:
```clj
user> (core/get-object-url s3-record "/test/foo.png")
"https://hydrogen-test.s3.eu-west-1.amazonaws.com/b?X-Amz-Algorithm=
AWS4-HMAC-SHA256&X-Amz-Date=20190708T141105Z&X-Amz-SignedHeaders=
host&X-Amz-Expires=3599&X-Amz-Credential=AKIAIDDFKQWZPM5QD2RA%2F2
0190708%2Feu-west-1%2Fs3%2Faws4_request&X-Amz-Signature=d34620a62
4dfec7ef3b943e1bec1d80e41bf97d3b391dc26a15aaf885e1f08b4"
```

#### `get-object-url [record object-id opts]`

* description: Gets a presigned URL that can be used to access the specified object without authentication. The URL lifespan is specified in the record initialization.
* parameters: 
  - `record`: An `AWSS3Bucket` record.
  - `object-id`: The key for the object in the S3 bucket that you want to access without authentication.
  - `opts`: A map with extra parameters that will be passed to the underlying [Amazonica S3 library](https://github.com/mcohen01/amazonica). See the [javadoc](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/AmazonS3Client.html#generatePresignedUrl-java.lang.String-java.lang.String-java.util.Date-com.amazonaws.HttpMethod-) for the keys that can be used here. This is tipically used to specify the allowed HTTP methods (PUT, DELETE, etc.)
* return value:
  - Returns a presigned URL that can be used to access the specified object without authentication, using the options specified above.

* Example:

```clj
user> (core/get-object-url s3-record "/test/foo.png" {:method "DELETE"})
"https://hydrogen-test.s3.eu-west-1.amazonaws.com/b?X-Amz-Algorithm=
AWS4-HMAC-SHA256&X-Amz-Date=20190708T141151Z&X-Amz-SignedHeaders=
host&X-Amz-Expires=3599&X-Amz-Credential=AKIAIDAGKQWZPM5QD2RA%2F201907
08%2Feu-west-1%2Fs3%2Faws4_request&X-Amz-Signature=803cf5f2eb6b53f4588
6896121ce83695df47d5f3ea274617a73cdec3090a0e6"
```

## Testing

The library includes self-contained units tests, including some integration tests that depend on AWS S3 service. Those tests have the `^:integration` metadata keyword associated to them, so you can exclude them from your unit tests runs.

If you want to run the integration tests, the following set of environment variables are needed (the first three are the standard AWS credentials environment variables):

* `AWS_ACCESS_KEY_ID`: The Access key ID of an AWS IAM user. That user must have permission to perform the various S3 operations (`PutObject`, `GetObject`, etc.) in the S3 bucket configured below.
* `AWS_SECRET_ACCESS_KEY`: The Secret Access key associated to the previous Access key ID.
* `AWS_DEFAULT_REGION`: The region where the S3 bucket is located at.
* `TEST_OBJECT_STORAGE_S3_BUCKET`: The name of the S3 bucket where the integration tests will upload, download and delete the objects.

## License

Copyright (c) Magnet S Coop 2018, 2019.

The source code for the library is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
