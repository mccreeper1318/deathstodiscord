# Changelog

All notable changes to DeathsToDiscord are documented here.

## [1.4.1] - 8/30/26

### Fixed

- Fixed issue #7: deaths that occur while a Discord update is in flight now trigger one fresh follow-up update instead of being missed.
- Fixed issue #8: historical-player discovery is cached, filesystem work and leaderboard formatting run asynchronously, and Bukkit profile/score lookups are spread across bounded tick batches to prevent large player histories from stalling one server tick on every update.
- Fixed issue #9: overlapping updates can no longer create multiple initial Discord leaderboard messages.
- Fixed issue #10: Discord updates are serialized so an older leaderboard snapshot cannot finish after and overwrite a newer snapshot.
- Fixed issue #11: Discord HTTP 429 responses honor the requested retry delay and retry without dropping the pending leaderboard update.
- Fixed issue #12: a deleted Discord leaderboard message is detected and recreated automatically on the next update.
- Fixed issue #15: Discord response payloads are parsed with a JSON parser instead of formatting-sensitive string searches.
- Fixed issue #16: Discord message IDs now live in a dedicated state file, are bound to a non-secret webhook fingerprint, migrate from legacy configuration, and cannot cross webhook reload sessions.
- Fixed issue #17: invalid mode, count, delay, boolean, URL, objective, and Discord content-limit settings now produce clear configuration errors instead of being silently normalized or ignored.
- Fixed issue #18: `/d2d reload` tab completion is no longer shown to senders without `d2d.admin`.
- Fixed issue #19: leaderboard updates now use Discord-native timestamps that render in each viewer's locale and timezone.
- Fixed issue #20: player names are escaped before being placed in Discord Markdown.
- Fixed issue #21: event handling, commands, configuration, player discovery, score collection, message formatting, Discord networking, JSON parsing, request coordination, and persisted state are now separated into focused components.
- Fixed issue #29: transient Discord PATCH failures caused by timeouts, connection errors, HTTP 408 responses, or HTTP 5xx responses now retry with bounded exponential backoff.
- Fixed issue #30: messages whose creation completes while the plugin is being disabled are cleaned up without relying on a new plugin-scheduled task.
- Fixed issue #31: malformed Discord retry delays are capped at five minutes so a single HTTP 429 response cannot stall updates for years.
- Fixed issue #32: Discord message state is written with atomic replacement and a recovery backup to prevent interrupted writes from creating duplicate leaderboard messages.
- Historical-player discovery now merges Bukkit's complete offline-player registry with first-world player-data files so existing scores remain visible when a player's `.dat` file is stored elsewhere.

### Security

- Fixed issue #13: malformed webhook URLs and Discord request failures can no longer expose webhook tokens in logs or administrator-facing errors.
