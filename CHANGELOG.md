# Change Log
All notable changes to this project will be documented in this
file. This change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

### Added
- Added a new `init-record` function, to be able to use the library from projects that don't use Integrant.
- Added new metadata option for `put-object` method (`:filename`), to specify the filename used in the Content-Disposition header when getting the object from S3.

### Changed
- Upgraded to object-storage.core 0.1.8

## [0.7.4] - 2026-02-03
### Added
- Added new metadata options for `put-object` method: `:content-type`, `:content-disposition` and `:content-encoding` options.
- Added `:metadata` key as a required value (that may be nil) in the map returned by `get-object`, when successfully executed.

### Changed
- Upgraded Clojure version to 1.12.4 and spec.alpha to 0.5.238.
- Upgraded Amazonica library to latest stable version.
- Upgraded AWS Java SDK to latest stable version.
- Upgraded to object-storage.core 0.1.7
- Upgraded linting and testing dependencies to latest stable versions, and Java version to 21 (for tests).
- Upgraded github Actions dependencies.
- copy-object no longer returns failure when the source object key and the destination object key are the same value. It does nothing and returns success.

## [0.7.3] - 2024-06-26
### Fixed
- Upgrade Amazonica to latest version, to make the S3 client side encryption respect specified endpoints. Otherwise it prevented the whole thing from working if we were using a S3-compatible service from another cloud provider.

## [0.7.2] - 2024-05-29
### Fixed
- Keep the `:endpoint` and `:explicit-object-acl` in the AWSS3Bucket record. When we added those two options, we forgot to make them part as the record. The implementation worked because Clojure records double as Clojure maps when we assoc extra keys to them. But they should be proper record members.
- When doing `put-object` operations on an AWSS3Bucket record with both an `:endpoint` and an `explicit-object-acl`, the underlying library generated a warning about not being able to set the S3 bucket account owner for the ACL. It turns out the heuristics used by the underlying library to get the account owner don't work if using an explicit `:endpoint` that doesn't look like the canonical AWS S3 enpoints. Which happens when using S3 compatible APIs from other cloud vendors.

### Changed
- Bump `eastwood`, `leinclj-fmt`, `digest`, `http-kit`, `amazonica` and AWS Java SDK dependencies.

## [0.7.1] - 2024-03-19
### Added
- Add option to upload files with specific ACL
- Add the option to get public URL

## [0.7.0] - 2023-05-07
### Changed
- Bump `integrant`, `amazonica` and AWS Java SDK dependencies.

### Added
- Add javax.xml.bind/jaxb-api dependency to avoid falling back to slower SDK implementations for certain operations.
- Add S3 adapter `:endpoint` configuration parameter, to be able to use specific AWS S3 endpoints, or use alternative S3-compatible services (like the ones offered by various cloud providers).

## [0.6.10] - 2022-05-23
### Changed
- Use the latest version of object-storage.core lib.

### Fixed
- Fix clj code indendation in README file.

## [0.6.9] - 2022-05-23
### Changed
- Moving the repository to [gethop-dev](https://github.com/gethop-dev) organization
- CI/CD solution switch from [TravisCI](https://travis-ci.org/) to [GitHub Actions](Ihttps://github.com/features/actions)
- `lein`, `cljfmt` and `eastwood` dependencies bump
- Fix several `eastwood` warnings
- update this changelog's releases tags links

### Added
- Source code linting using [clj-kondo](https://github.com/clj-kondo/clj-kondo).

## [0.6.8] - 2021-12-17
### Changed
- Bump `object-storage.core` dependency to get updated specs for `get-object-url-opts`.

## [0.6.7] - 2021-12-16
### Fixed
- Bump `http-kit` dependency to fix SSL+SNI Connection Errors.

## [0.6.6] - 2021-12-16
### Fixed
- Fix formatting errors.

## [0.6.5] - 2021-12-16
### Changed
- Update `get-object-url` opts to accept `content-type` and `content-disposition`.

## [0.6.4] - 2020-05-05
### Added
- Add copy-object method.

## [0.6.3] - 2020-02-11
### Fixed
- Change list-objects to handle S3 buckets with more than 1000 keys (the page limit for listObjectsV2 API endpoint).

## [0.6.2] - 2020-02-11
### Changed
- Change list-objects to use `pmap` instead of `map`.

## [0.6.1] - 2020-02-10
### Added
- Add list-objects method.

## [0.6.0] - 2019-12-05
### Changed
- Use the protocol definition in [dev.gethop.object-storage.core](https://github.com/gethop-dev/object-storage.core) instead of a local definition (it doesn't affect the functionality).

[Unreleased]: https://github.com/gethop-dev/object-storage.s3/compare/v0.7.4...HEAD
[0.7.4]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.7.4
[0.7.3]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.7.3
[0.7.2]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.7.2
[0.7.1]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.7.1
[0.7.0]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.7.0
[0.6.10]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.6.10
[0.6.9]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.6.9
[0.6.8]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.6.8
[0.6.7]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.6.7
[0.6.6]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.6.6
[0.6.5]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.6.5
[0.6.4]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.6.4
[0.6.3]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.6.3
[0.6.2]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.6.2
[0.6.1]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.6.1
[0.6.0]: https://github.com/gethop-dev/object-storage.s3/releases/tag/v0.6.0
