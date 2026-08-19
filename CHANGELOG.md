# Changelog

All notable changes to DeathsToDiscord are documented here.

## [1.4.1-beta.1] - Unreleased

### Added

- Added automated GitHub Actions build and release workflows for Java 25/Gradle builds, tests, verified plugin artifacts, and SHA-256 checksums.
- Added automated tests for leaderboard ordering, TOP mode behavior, Discord message-length trimming, and death-update scheduling.

### Changed

- Changed the Gradle project version to `1.4.1` as the base version for the `1.4.1` prerelease/stable series.
- Changed `plugin.yml` version handling so the plugin version is sourced from Gradle during the build.
- Pinned the Paper 26.2 API dependency to a specific build for reproducible builds.
- Changed death-triggered update scheduling to track scheduled, in-flight, and pending update states while preserving the existing configurable debounce delay.

### Fixed

- Fixed issue #7: deaths that occur after a leaderboard snapshot starts, including while the Discord request is still in progress, now queue a fresh follow-up update instead of being silently missed.
- Fixed rapid in-flight deaths so they coalesce into one follow-up update rather than producing unnecessary duplicate requests.
- Fixed the Gradle 9.3 test runtime configuration by explicitly including the JUnit Platform launcher.
