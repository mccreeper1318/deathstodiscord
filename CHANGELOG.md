# Changelog

All notable changes to DeathsToDiscord are documented here.

## [1.4.1-beta.2] - Unreleased

### Added

- Added regression tests for initial Discord message creation coordination, including overlapping update requests and failed creation attempts.
- Added regression tests for serialized Discord PATCH ordering and for keeping fresh post-creation updates behind the creator PATCH.

### Changed

- Changed overlapping startup, reload, and death-triggered updates to wait for an in-progress initial Discord message creation when `message-id` is blank.
- Changed Discord leaderboard PATCH requests to run serially in submission order instead of concurrently.
- Changed initial message creation so its captured leaderboard PATCH completes before queued updates are released to rebuild and submit fresher snapshots.
- Preserved the existing 1.4/1.4.1 configuration format and saved `message-id` behavior; no configuration migration is required.

### Fixed

- Fixed issue #9: multiple overlapping updates can no longer create multiple Discord leaderboard messages while `message-id` is blank.
- Fixed queued updates after a failed initial message creation so their completion callbacks are released instead of remaining stuck.
- Fixed issue #10: concurrent PATCH requests can no longer finish out of order and allow an older leaderboard snapshot to overwrite a newer one.
- Fixed the initial-message race where queued fresh updates could be released before the creator's older PATCH, allowing the stale creator snapshot to become the final Discord message.

## [1.4.1-beta.1] - Released - 2026-08-19

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
