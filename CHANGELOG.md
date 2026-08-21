# Changelog

All notable changes to DeathsToDiscord are documented here.

## [1.4.1-beta.3] - Unreleased

### Added

- Added regression tests for Discord rate-limit retry delays, including `Retry-After` headers, Discord `retry_after` response bodies, fractional delays, malformed values, and safe fallback behavior.

### Changed

- Changed rate-limited Discord webhook message creation and leaderboard updates to retry asynchronously after Discord's requested delay instead of treating HTTP 429 responses as permanent failures.
- Preserved initial message creation coordination, serialized PATCH ordering, and pending death updates while a rate-limited request waits to retry.

### Fixed

- Fixed issue #11: Discord HTTP 429 responses no longer leave the leaderboard permanently stale when no later event triggers another update.

## [1.4.1-beta.2] - Released

### Added

- Added regression tests for initial Discord message creation coordination, including overlapping update requests and failed creation attempts.
- Added regression tests for serialized Discord PATCH ordering and for keeping fresh post-creation updates behind the creator PATCH.
- Added regression tests that verify Discord webhook secrets are removed from exception messages before they can be logged or shown to administrators.

### Changed

- Changed overlapping startup, reload, and death-triggered updates to wait for an in-progress initial Discord message creation when `message-id` is blank.
- Changed Discord leaderboard PATCH requests to run serially in submission order instead of concurrently.
- Changed initial message creation so its captured leaderboard PATCH completes before queued updates are released to rebuild and submit fresher snapshots.
- Changed Discord request error handling to sanitize exception details before reporting failures while preserving useful non-secret diagnostics.
- Preserved the existing 1.4/1.4.1 configuration format and saved `message-id` behavior; no configuration migration is required.

### Fixed

- Fixed issue #9: multiple overlapping updates can no longer create multiple Discord leaderboard messages while `message-id` is blank.
- Fixed queued updates after a failed initial message creation so their completion callbacks are released instead of remaining stuck.
- Fixed issue #10: concurrent PATCH requests can no longer finish out of order and allow an older leaderboard snapshot to overwrite a newer one.
- Fixed the initial-message race where queued fresh updates could be released before the creator's older PATCH, allowing the stale creator snapshot to become the final Discord message.

### Security

- Fixed issue #13: malformed webhook URLs and related Discord request exceptions can no longer expose the configured webhook URL or token through server logs or administrator-facing failure messages.
- Added defense-in-depth redaction for Discord webhook URLs found in exception text even when they do not exactly match the currently configured webhook value.

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
