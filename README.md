[![ci-cd](https://github.com/gethop-dev/object-storage.s3/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/gethop-dev/object-storage.s3/actions/workflows/ci-cd.yml)
[![Clojars Project](https://img.shields.io/clojars/v/dev.gethop/object-storage.s3.svg)](https://clojars.org/dev.gethop/object-storage.s3)


# Duct Object Storage S3

A [Duct](https://github.com/duct-framework/duct) library that provides [Integrant](https://github.com/weavejester/integrant) keys for managing AWS S3 objects, implementing the [ObjectStorage](https://github.com/gethop-dev/object-storage.core) protocol.

## Installation

[![Clojars Project](https://clojars.org/dev.gethop/object-storage.s3/latest-version.svg)](https://clojars.org/dev.gethop/object-storage.s3)

## Usage

### Getting an `AWSS3Bucket` record

This library provides a single Integrant key, `:dev.gethop.object-storage/s3`, that returns an `AWSS3Bucket` record that can be used to perform `ObjectStorage` protocol operations on a given S3 bucket. For the `get-object-url` operation it generates a presigned URL that can be used to access objects in private buckets without holding any AWS credentials, with a limited life span. The key initialization accepts the following keys:

* `:bucket-name`: A string with the name of the bucket where we want to perform S3 operations. This key is mandatory.
* `:presigned-url-lifespan`: A number with the lifespan for the presigned URLs. It is specified in minutes (fractional values can be used). This key is optional. If not provided, the default value is one hour.
* `:endpoint`: A string with the URL of the S3 service endpoint the adapter will use. This key is optional. If not provided, the endpoint is determined by the AWS SDK (using its own standard criteria).
* `:explicit-object-acl`: A map with the ACL configuration the adapter will use to create all new objects. The key is optional. If not provided, objects will inherit the access configuration of the bucket.

Example configuration, with a presigned URL life span of 30 minutes:

``` edn
 :dev.gethop.object-storage/s3 {:bucket-name "hydrogen-test"
                                :presigned-url-lifespan 30}
```

Another example configuration, with a presigned URL life span of 10 minutes, specifying a particular endpoint (for the S3-compatible OVH Object Storage service in this case):

``` edn
 :dev.gethop.object-storage/s3 {:bucket-name "ovh-object-store-bucket"
                                :presigned-url-lifespan 10
                                :endpoint "https://s3.rbx.io.cloud.ovh.net"}
```

Another example configuration, with a presigned URL life span of 15 minutes, specifying a particular endpoint (for the S3-compatible OVH Object Storage service in this case) and with an ACL policy set to allow all users to `Read` the object:

``` edn
 :dev.gethop.object-storage/s3 {:bucket-name "ovh-object-store-bucket"
                                :presigned-url-lifespan 15
                                :endpoint "https://s3.rbx.io.cloud.ovh.net"
                                :explicit-object-acl {:grant-permission ["AllUsers" "Read"]}}
```

### Performing S3 object operations

We need to require the `dev.gethop.object-storage.core` namespace to get the `ObjectStorage` protocol definition.

``` clj
user> (require '[dev.gethop.object-storage.core :as object-storage])
nil
```

Then we initiate the integrant key with an example configuration to get our S3 boundary `AWSSEBucket` record:

```clj
user> (def config {:bucket-name "hydrogen-test"
                   :presigned-url-lifespan 30})
#'user/config
user> (require '[dev.gethop.object-storage.s3]
               '[integrant.core :as ig])
nil
user> (def s3-boundary (ig/init-key :dev.gethop.object-storage/s3 config))
#'user/s3-boundary
```

Once we have the protocol in place, we can use the `AWSS3Bucket` record to perform the following operations.

#### `(put-object s3-boundary object-id object)`

* description: Uploads an object to S3 with `object-id` as its S3 key.
* parameters:
  - `s3-boundary`: An `AWSS3Bucket` record.
  - `object-id`: The S3 key for the object that we want to upload.
  - `object`: The file we want to upload (as a `java.io.File`-compatible value).
* return value: a map with the following keys:
  - `:success?`: boolean stating if the operation was successful or not.
  - `:error-details`: a map with additional details on the problem encountered while trying to upload the object.

Let's see an example. First for a successful invocation:

```clj
user> (require '[clojure.java.io :as io])
user> (object-storage/put-object s3-boundary "some-s3-key" (io/file "some-existing-file"))
{:success? true}
```

Then for a failed one, because we don't have upload permissions:

``` clj
user> (object-storage/put-object s3-boundary
                                 "some-s3-key-without-permissions"
                                 (io/file "some-existing-file"))
{:success? false,
 :error-details
 {:error-code "AccessDenied",
  :error-type "Client",
  :status-code 403,
  :request-id "07308DA9AF455078",
  :service-name "Amazon S3",
  :message
  "Access Denied (Service: Amazon S3; Status Code: 403; Error Code: AccessDenied; Request ID: 07308DA9AF455078; S3 Extended Request ID: gaJ8voOi5pO3HJ/yZt5mIAMUvpVycgekOyvihgc/XBGg8gehNkzMAiDbYtZEq0ckErXSGN3yrZI=)"}}
```

#### `(put-object s3-boundary object-id object opts)`

* description: Uploads an object to S3 with `object-id` as its key, using additional options.
* parameters:
  - `s3-boundary`: An `AWSS3Bucket` record.
  - `object-id`: The S3 key for the object that we want to upload.
  - `object`: The file we want to upload. It can be either a `java.io.File`-compatible value, or an `java.io.InputStream`-compatible value. In the latter case, if you know the size of the content in the InputStream, add the `:metadata` key to the `opts` map, setting the `object-size` key with the size of the InputStream content.
  - `opts: A map of options. Currently supported option keys are:
    - `metadata`: It is a map with the following supported keys:
      - `:object-size`: The size, in bytes, of the `object` passed in as an InputStream. If you don't specify it, the library needs to read the whole input into memory before uploading it. This could cause out of memory issues for large objects.
      - `:content-type`: The value for the `Content-Type` header that will be used, unless overridden, when downloading the object.
      - `:content-disposition`: A keyword wit the type of `Content-Disposition` header that will be used, unless overridden, when downloading the object. It can be eiher `:inline` or `attachment`.
      - `:content-encoding`: The value for the `Content-Type` header that will be used, unless overridden, when downloading the object.
    - `:encryption`: It is a map with the following supported keys for client side encryption:
      - `:secret-key`: Any AmazonS3EncryptionClient supported symmetric key (e.g., AES256, AES128, etc.)
      - `:key-pair`:  Any AmazonS3EncryptionClient supported asymmetric key (e.g., RSA. EC, etc.)
* return value: a map with the following keys:
  - `:success?`: boolean stating if the operation was successful or not.
  - `:error-details`: a map with additional details on the problem encountered while trying to retrieve the object.


Let's see an example. We want to upload an object that is an InputStream instead of a `File`. A typical use case for this scenario is that the object that we want to upload to S3 is a file that is being uploaded from an HTTP client. Ring adapters usually provides us with an InputStream, instead of a File. In this example we mock it by using a string and creating an InputStream from it:

```clj
user> (let [object-content (.getBytes "Test")
            object-size (count object-content)]
        (object-storage/put-object s3-boundary
                                   "some-s3-key"
                                   (io/input-stream object-content)
                                   {:metadata {:object-size object-size}}))
{:success? true}
```

A second example to show how to upload an object (again, as an mocked InputStream), and set the Content-Type and Content-Disposition headers for the retrieval of the object. In this case we want the retrieval operation to offer the end-user to save the content of the object as a file, instead of directly trying to open or diplay it. We set the Content-Type header to "image/png":


```clj
user> (let [object-content (.getBytes "Test")
            object-size (count object-content)]
        (object-storage/put-object s3-boundary
                                   "some-s3-key"
                                   (io/input-stream object-content)
                                   {:metadata {:object-size object-size
                                               :content-disposition :attachment
                                               :content-type "image/png"}}))
{:success? true}
```


The other use case is when we want to put an encrypted object in S3, doing encryption client side. In this example we use symmetric key encryption algorithm (AES256):

``` clj
user> (import '[javax.crypto KeyGenerator]
              '[java.security SecureRandom])
java.security.SecureRandom
user> (def aes256-key
        (let [kg (KeyGenerator/getInstance "AES")]
          (.init kg 256 (SecureRandom.))
          (.generateKey kg)))
#'user/aes256-key
user> (object-storage/put-object s3-boundary
                                 "some-s3-key"
                                 (io/file "some-file")
                                 {:encryption {:secret-key aes256-key}})
{:success? true}
```

#### `(copy-object s3-boundary source-object-id destination-object-id)`

* description: Copies an object from S3 into the same Bucket.
* parameters:
  - `s3-boundary`: An `AWSS3Bucket` record.
  - `source-object-id`: The key of the object in the S3 bucket that we want to copy.
  - `destination-object-id`: The key of the object in the S3 bucket as result of the copied object.
* return value: a map with the following keys:
  - `:success?`: boolean stating if the operation was successful or not.
  - `:error-details`: a map with additional details on the problem encountered while trying to copy the object.

Let's see an example for a successful invocation:

```clj
user> (object-storage/copy-object s3-boundary "some-existing-source-s3-key" "new-destination-s3-key")
{:success? true}
```

Copying a bucket key to itself using this method is a no-op, and always succeeds.

#### `(copy-object s3-boundary source-object-id destination-object-id opts)`

* description: Copies an object from S3 into the same Bucket using additional options.
* parameters:
  - `s3-boundary`: An `AWSS3Bucket` record.
  - `source-object-id`: The key of the object in the S3 bucket that we want to copy.
  - `destination-object-id`: The key of the object in the S3 bucket as result of the copied object.
  - `opts`: A map of options. Currently we do not support any yet.
* return value: a map with the following keys:
  - `:success?`: boolean stating if the operation was successful or not.
  - `:error-details`: a map with additional details on the problem encountered while trying to copy the object.

No need for examples for this case yet.

#### `(get-object s3-boundary object-id)`

* description: Retrieves an object from S3.
* parameters:
  - `s3-boundary`: An `AWSS3Bucket` record.
  - `object-id`: The key of the object in the S3 bucket that we want to retrieve.
* return value: a map with the following keys:
  - `:success?`: boolean stating if the operation was successful or not.
  - `:object`: If the operation was successful, this key contains an `InputStream`-compatible stream, on the desired object. Note that the `InputStream` returned by `get-object` should be closed (.e.g, via slurp) or the HTTP connection pool will be exhausted after several objects are retrieved.
  - `:error-details`: a map with additional details on the problem encountered while trying to retrieve the object.

Let's see an example. First for a successful invocation:

```clj
user> (object-storage/get-object s3-boundary "some-existing-s3-key")
{:success? true,
 :object
 #object[com.amazonaws.services.s3.model.S3ObjectInputStream 0x710e43ea
 "com.amazonaws.services.s3.model.S3ObjectInputStream@710e43ea"]}
```

Then for a failed one, requesting a non existing bucket key:

``` clj
user> (object-storage/get-object s3-boundary "some-non-existing-s3-key")
{:success? false,
 :error-details
 {:error-code "NoSuchKey",
  :error-type "Client",
  :status-code 404,
  :request-id "2FE3FC50AC75E6AF",
  :service-name "Amazon S3",
  :message "The specified key does not exist. [...rest omitted for brevity]"
```

#### `(get-object s3-boundary object-id opts)`

* description: Retrieves an object from S3 using additional options
* parameters:
  - `s3-boundary`: An `AWSS3Bucket` record.
  - `object-id`: The key of the object in the S3 bucket that we want to retrieve.
  - `opts: A map of options. Currently supported option keys are:
    - `:encryption`: It is a map with the following supported keys for client side encryption:
      - `:secret-key`: Any AmazonS3EncryptionClient supported symmetric key (e.g., AES256, AES128, etc.)
      - `:key-pair`:  Any AmazonS3EncryptionClient supported asymmetric key (e.g., RSA. EC, etc.)
* return value: a map with the following keys:
  - `:success?`: boolean stating if the operation was successful or not.
  - `:object`: If the operation was successful, this key contains an `InputStream`-compatible stream, on the desired object. Note that the `InputStream` returned by `get-object` should be closed (.e.g, via slurp) or the HTTP connection pool will be exhausted after several objects are retrieved.
  - `:error-details`: a map with additional details on the problem encountered while trying to retrieve the object.

Let's see an example where we retrieve the encrypted object that we put in S3 earlier. We retrieve it from S3 (still encrypted) and decrypt it using client side encryption with the same symmetric key (AES256):

```clj
user> (object-storage/get-object s3-boundary
                                 "some-s3-key"
                                 {:encryption {:secret-key aes256-key}})
{:success? true,
 :object
 #object[com.amazonaws.services.s3.model.S3ObjectInputStream 0x5e7fc790
 "com.amazonaws.services.s3.model.S3ObjectInputStream@5e7fc790"]}
user>
```

Let's also see what happens if we use the wrong cryptographic key for client side decryption:

```clj
user> (def another-aes256-key
        (let [kg (KeyGenerator/getInstance "AES")]
          (.init kg 256 (SecureRandom.))
          (.generateKey kg)))
#'user/another-aes256-key
user> (object-storage/get-object s3-boundary
                                 "some-s3-key"
                                 {:encryption {:secret-key another-aes256-key}})
{:success? false,
 :error-details
 {:message "Unable to decrypt symmetric key from object metadata",
  :error-type "Client"}}
```

#### `(delete-object s3-boundary object-id)`

* description: Deletes the object with key `object-id` from S3.
* parameters:
  - `s3-boundary`: An `AWSS3Bucket` record.
  - `object-id`: The key of the object in the S3 bucket that we want to delete.
* return value: a map with the following keys:
  - `:success?`: boolean stating if the operation was successful or not.
  - `:error-details`: a map with additional details on the problem encountered while trying to retrieve the object.

Let's see an example. First for a successful invocation:

```clj
user> (object-storage/delete-object s3-boundary "some-s3-key")
{:success? true}
```

Then for a failed one, because we don't have delete permissions:

``` clj
user> (object-storage/delete-object s3-boundary
                                   "test-forbidden/some-s3-key")
{:success? false,
 :error-details
 {:error-code "AccessDenied",
  :error-type "Client",
  :status-code 403,
  :request-id "A44EBBA686B2CD4F",
  :service-name "Amazon S3",
  :message
  "Access Denied (Service: Amazon S3; Status Code: 403; Error Code: AccessDenied; Request ID: A44EBBA686B2CD4F; S3 Extended Request ID: KCfsfr1LqTHVNMZhhqme8sUQN8xb6vxTt8qZvjw5qzQB45N6GK+/AOHmepNru1eNq4N3yHw6htM=)"}}
```

#### `(rename-object s3-boundary source-object-id destination-object-id)`

* description: Renames an object from S3 into the same Bucket.
* parameters:
  - `s3-boundary`: An `AWSS3Bucket` record.
  - `source-object-id`: The key of the object in the S3 bucket that we want to rename.
  - `destination-object-id`: The key of the object in the S3 bucket that will be result of the renaming of object.
* return value: a map with the following keys:
  - `:success?`: boolean stating if the operation was successful or not.
  - `:error-details`:  map with additional details on the problem encountered while trying to rename the object. This key is only present if `:success?` is `false`.

Let's see an example for a successful invocation:

```clj
user> (object-storage/rename-object s3-boundary "some-existing-source-s3-key" "new-destination-s3-key")
{:success? true}
```

Renaming a bucket key to itself using this method is a no-op, and always succeeds.

#### `(get-object-url s3-boundary object-id)`

* description: Gets a presigned URL that can be used to get the specified object without authentication. The URL lifespan is specified in the `AWSS3Bucket` record initialization.
* parameters:
  - `s3-boundary`: An `AWSS3Bucket` record.
  - `object-id`: The key of the object in the S3 bucket that we want to get  without authentication.
* return value: a map with the following keys:
  - `:success?`: boolean stating if the operation was successful or not.
  - `:object-url`: If the operation was successful, this key contains a string with a presigned URL that can be used to get the specified object without authentication, but only within the configured lifespan. In addition, the presigned URL is only valid for GET requests.
  - `:error-details`: a map with additional details on the problem encountered while trying to create the presigned URL.

Here is an example of a successful execution:

```clj
user> (object-storage/get-object-url s3-boundary "some-s3-key")
{:success? true,
 :object-url "https://hydrogen-test.s3.eu-west-1.amazonaws.com/some-s3-key?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20191101T204154Z&X-Amz-SignedHeaders=host&X-Amz-Expires=1799&X-Amz-Credential=AKIAIIOB7F5TDVMDNXRQ%2F20191101%2Feu-west-1%2Fs3%2Faws4_request&X-Amz-Signature=06117e5dbf7a7315514e0ee2fa2ac5375aa6511481931455b814bb10fcefeec4"}
```

#### `(get-object-url s3-boundary object-id opts)`

* description: Gets a presigned URL that can be used to access the specified object without authentication, using special options. The URL lifespan is specified in the `AWSS3Bucket` record initialization.
* parameters:
  - `s3-boundary`: An `AWSS3Bucket` record.
  - `object-id`: The key of the object in the S3 bucket that we want to access without authentication.
  - `opts`: A map of options. Currently supported option keys are:
    - `:method`: Specifies the operation that we want to use with the presigned URL. It can be one of the following:
      - `:create`: Allows using a HTTP PUT request.
      - `:read`:  Allows using a HTTP GET request.
      - `:update`: Allows using a HTTP PUT request.
      - `:delete`: Allows using a HTTP DELETE request.
    - `:filename`: Specifies the filename that will be included in the "Content-Disposition" header for `:read` requests. It allows retrieving the object with a different name that the S3 key it was stored under.
    - `:content-type`: Specifies the value that will be included in the "Content-Type" header. Uses "application/octet-stream" as default if unspecified. Requires `filename` to be present in the opts.
    - `:content-disposition`: Specifies the value that will be included in the "Content-Disposition" header. Has to be either `:inline` or `:attachment`. Defaults to `:attachment`. Requires `filename` to be present in the opts.
    - `:object-public-url?`: A boolean that specifies if the URL given by this function is going to be a public accessible one, instead of presigned. This only give us the url, the object should be accessible this way for this URL work. 
* return value: a map with the following keys:
  - `:success?`: boolean stating if the operation was successful or not.
  - `:object-url`: If the operation was successful, this key contains a string with a presigned URL that can be used to get the specified object without authentication, but only within the configured lifespan. In addition, the presigned URL is only valid for GET requests.
  - `:error-details`: a map with additional details on the problem encountered while trying to create the presigned URL.

Let's see an example. First a presigned URL for a `:create` operation:

```clj
user> (object-storage/get-object-url s3-boundary
                                     "some-s3-key"
                                     {:method :create})
{:success? true,
 :object-url
 "https://hydrogen-test.s3.eu-west-1.amazonaws.com/some-s3-key?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20191101T205923Z&X-Amz-SignedHeaders=host&X-Amz-Expires=1799&X-Amz-Credential=AKIAIIOB7F5TDVMDNXRQ%2F20191101%2Feu-west-1%2Fs3%2Faws4_request&X-Amz-Signature=f349b0068b1c746cda25d8b98af5ec8cf8f718010a9fc2ecc4a072ed9f05fa25"}
```

Then for a `:read` operation where we want to set a specific filename for the object that we get from S3:

``` clj
user> (object-storage/get-object-url s3-boundary
                                     "some-s3-key"
                                     {:method :read
                                      :filename "other-arbitrary-filename"})
{:success? true,
 :object-url
 "https://hydrogen-test.s3.eu-west-1.amazonaws.com/some-s3-key?response-content-disposition=attachment%3B%20filename%3Dother-arbitrary-filename&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20191101T210217Z&X-Amz-SignedHeaders=host&X-Amz-Expires=1799&X-Amz-Credential=AKIAIIOB7F5TDVMDNXRQ%2F20191101%2Feu-west-1%2Fs3%2Faws4_request&X-Amz-Signature=13f2bb4b32c5efb0cf4ce8c2aeba66eed1d483dd8d4ec2d93b03fefd94f0f525"}
```

#### `(list-objects s3-boundary parent-object-id)`

* description: Gets a list of children objects from a given S3 object key.
* parameters:
  - `s3-boundary`: An `AWSS3Bucket` record.
  - `parent-object-id`: The key of the object in the S3 bucket that we want to access.
* return value: a map with the following keys:
  - `:success?`: boolean stating if the operation was successful or not.
  - `:objects`: If the operation was successful, this key contains a collection of maps.Each map represents a children object. Every object has 3 attributes: `object-id`, `last-modified` and `size`. Note that the collection returned will also contain the parent object since from the S3 perspective there is no folders concept.
  - `:error-details`: a map with additional details on the problem encountered while trying retrieve the list of objects.

Example:

```clj
user> (object-storage/list-objects s3-boundary "some-s3-key")
{:success? true,
 :objects
 ({:object-id "documents/",
   :last-modified
   #object[org.joda.time.DateTime 0x5d61cd29 "2018-11-27T10:09:08.000+01:00"],
   :size 0}
  {:object-id "documents/a",
   :last-modified
   #object[org.joda.time.DateTime 0x6bdf58ef "2018-11-28T11:12:07.000+01:00"],
   :size 15}
  {:object-id "documents/download.png",
   :last-modified
   #object[org.joda.time.DateTime 0x3b389e64 "2018-11-29T10:20:09.000+01:00"],
   :size 3400})}
```

## Testing

The library includes self-contained units tests, including some integration tests that depend on AWS S3 service. Those tests have the `^:integration` metadata keyword associated to them, so you can exclude them from our unit tests runs.

If you want to run the integration tests, the following set of environment variables are needed (the first three are the standard AWS credentials environment variables):

* `AWS_ACCESS_KEY_ID`: The Access key ID of an AWS IAM user. That user must have permission to perform the various S3 operations (`PutObject`, `GetObject`, etc.) in the S3 bucket configured below.
* `AWS_SECRET_ACCESS_KEY`: The Secret Access key associated to the previous Access key ID.
* `AWS_DEFAULT_REGION`: The region where the S3 bucket is located at.
* `TEST_OBJECT_STORAGE_S3_BUCKET`: The name of the S3 bucket where the integration tests will upload, download and delete the objects.

## License

Copyright (c) 2024 Biotz. SL.

The source code for the library is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
