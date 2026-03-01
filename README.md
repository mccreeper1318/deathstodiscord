# DeathsToDiscord Changelog

## Version 1.0.0

* Initial release
* Reads deaths scoreboard objective
* Posts leaderboard to Discord via webhook
* Supports configurable update interval
* Supports ALL players or TOP N display modes
* Basic config.yml setup

## Version 1.1.0

* Switched from interval updates to updating on every player death
* Added automatic creation of a single Discord message
* Implemented PATCH editing to update the same message (no spam)
* Added message-id storage in config.yml
* Added update delay batching to prevent Discord rate limits
* Added /d2d reload command for live config reload
* Added permission node: d2d.admin

## Version 1.2.0

* Improved leaderboard formatting
* Enhanced error handling for missing objective or webhook
* Added automatic startup update
* Improved scoreboard parsing reliability
* Improved config validation and console logging

## Version 1.2.1

* Fixed "Invalid HTTP method: PATCH" error
* Replaced HttpURLConnection with Java 21 HttpClient
* Improved PATCH request reliability and Discord compatibility
* Minor internal cleanup and stability improvements
