# Change Log
All notable changes to this project will be documented in this
file. This change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## Unreleased

## 0.6.8 - 2021-12-17
### Changed
- Bump `object-storage.core` dependency to get updated specs for `get-object-url-opts`

## 0.6.7 - 2021-12-16
### Fixed
- Bump `http-kit` dependency to fix SSL+SNI Connection Errors

## 0.6.6 - 2021-12-16
### Fixed
- Fix formatting errors

## 0.6.5 - 2021-12-16
### Changed
- Update `get-object-url` opts to accept `content-type` and `content-disposition`

## 0.6.4 - 2020-05-05
### Added
- Add copy-object method

## 0.6.3 - 2020-02-11
### Fixed
- Change list-objects to handle S3 buckets with more than 1000 keys
  (the page limit for listObjectsV2 API endpoint)

## 0.6.2 - 2020-02-11
### Changed
- Change list-objects to use `pmap` instead of `map`.

## 0.6.1 - 2020-02-10
### Added
- Add list-objects method

## 0.6.0 - 2019-12-05
### Changed
- Use the protocol definition in [magnet.object-storage.core](https://github.com/magnetcoop/object-storage.core) instead of a local definition (it doesn't affect the functionality)
