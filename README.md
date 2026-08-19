# DeathsToDiscord

DeathsToDiscord is a lightweight Paper plugin that keeps a Minecraft death leaderboard synced to a single Discord webhook message.

Instead of posting a new Discord message every time someone dies, the plugin creates one leaderboard message and updates it as deaths occur. It can display every tracked player or only the top players, include or hide zero-death players, and automatically trims very large leaderboards to stay within Discord's message limit.

## Features

- Updates the Discord leaderboard whenever a player dies
- Uses a single Discord message instead of creating message spam
- Supports `ALL` and `TOP` leaderboard modes
- Includes offline players that have been tracked by the server
- Optional display of players with zero deaths
- Automatically saves and reuses the Discord message ID
- Batches rapid deaths with a configurable update delay
- Protects against Discord's 2,000-character message limit
- Reloadable configuration without restarting the server
- No additional plugin dependencies

## Requirements

- Paper **26.2**
- Java **25**
- A Discord server where you can create a webhook
- A Minecraft scoreboard objective that tracks player deaths

## Installation

1. Download the latest DeathsToDiscord `.jar` from the [GitHub Releases](https://github.com/mccreeper1318/deathstodiscord/releases) page.
2. Stop your Minecraft server.
3. Place the `.jar` in the server's `plugins` folder.
4. Start the server once to generate the configuration file.
5. Configure your Discord webhook and scoreboard objective in `plugins/DeathsToDiscord/config.yml`.
6. Restart the server, or run `/d2d reload` after saving the configuration.

## Discord Webhook Setup

1. Open the Discord server and choose the channel where the leaderboard should appear.
2. Open **Server Settings → Integrations → Webhooks**.
3. Create a new webhook and select the desired channel.
4. Copy the webhook URL.
5. Paste it into `webhook-url` in the DeathsToDiscord configuration.

Treat the webhook URL like a password. Anyone with the URL can use that webhook to post to its Discord channel.

## Minecraft Scoreboard Setup

DeathsToDiscord reads an existing scoreboard objective. It does not create or increment the death objective itself.

To create a standard death counter named `deaths`, run:

```text
/scoreboard objectives add deaths deathCount "Deaths"
```

To see the objectives already available on your server, run:

```text
/scoreboard objectives list
```

If your death objective uses a different name, set that name under `objective-name` in `config.yml`.

## Configuration

Configuration file:

```text
plugins/DeathsToDiscord/config.yml
```

Default configuration:

```yaml
webhook-url: "PASTE_WEBHOOK_URL_HERE"

# Your scoreboard objective name from /scoreboard objectives list
objective-name: "deaths"

# "ALL" = list everyone
# "TOP" = list top N only
mode: "ALL"
top: 10

# If true, players with 0 deaths are included in the leaderboard
show-zero-deaths: true

# The plugin will create the first message and fill this automatically
message-id: ""

# Small delay to batch rapid deaths & avoid rate limits
update-delay-seconds: 2

# Discord message content can be up to 2000 characters.
# This leaves a small safety buffer and trims very large leaderboards instead of failing.
max-discord-content-characters: 1900
```

### `webhook-url`

The Discord webhook URL used to create and update the leaderboard message.

```yaml
webhook-url: "PASTE_WEBHOOK_URL_HERE"
```

### `objective-name`

The Minecraft scoreboard objective DeathsToDiscord reads for each player's death count.

```yaml
objective-name: "deaths"
```

Use `/scoreboard objectives list` to verify the correct objective name.

### `mode`

Controls how many tracked players are displayed.

```yaml
mode: "ALL"
```

Available values:

- `ALL` — displays all tracked players that fit in the Discord message
- `TOP` — displays only the highest-ranked players, limited by the `top` setting

Players are sorted from the highest death count to the lowest. Ties are sorted alphabetically.

### `top`

Sets the number of players shown when `mode` is set to `TOP`.

```yaml
top: 10
```

The minimum effective value is `1`.

### `show-zero-deaths`

Controls whether players with zero deaths are included.

```yaml
show-zero-deaths: true
```

- `true` — include players with zero deaths
- `false` — hide players with zero deaths

### `message-id`

Stores the ID of the Discord leaderboard message.

```yaml
message-id: ""
```

Leave this blank during the initial setup. DeathsToDiscord creates the first message automatically and saves its ID here.

Normally, this value should not be edited manually. If the leaderboard message is deleted from Discord, clear the value, save the config, and run `/d2d reload` so the plugin can create a replacement message.

You should also clear it if you move the plugin to a different webhook or Discord channel and the existing message can no longer be edited through that webhook.

### `update-delay-seconds`

Adds a short delay before updating Discord after a death.

```yaml
update-delay-seconds: 2
```

This helps combine rapid deaths into one update and reduces unnecessary webhook requests. A value of `0` disables the delay.

### `max-discord-content-characters`

Sets the maximum size DeathsToDiscord will use for the leaderboard message.

```yaml
max-discord-content-characters: 1900
```

Discord allows a maximum of 2,000 characters for normal message content. DeathsToDiscord defaults to `1900` to leave a safety buffer. Configured values are limited to a minimum of `500` and a maximum of `2000`.

If the leaderboard is too large, the plugin keeps as many ranked players as will fit and adds a line showing how many additional players were omitted.

## Commands

### `/d2d reload`

Reloads `config.yml` and immediately updates the Discord leaderboard message.

Permission:

```text
d2d.admin
```

The permission defaults to server operators.

## How the Leaderboard Works

When DeathsToDiscord starts with a valid webhook configured, it immediately synchronizes the leaderboard.

If `message-id` is blank, the plugin creates a new Discord message and automatically saves its ID to `config.yml`. Future updates edit that same message.

Whenever a player dies, DeathsToDiscord waits for the configured `update-delay-seconds` value and then rebuilds the leaderboard from the configured scoreboard objective. Rapid deaths during the delay are combined into the same update.

A Discord leaderboard resembles:

```text
💀 Death Leaderboard (Everyone)
1. PlayerOne — 12
2. PlayerTwo — 7
3. PlayerThree — 2

Tracked players: 3
Updated: ...
```

## Troubleshooting

### The plugin says the webhook URL is not set

Open `plugins/DeathsToDiscord/config.yml`, replace `PASTE_WEBHOOK_URL_HERE` with a valid Discord webhook URL, save the file, and run `/d2d reload`.

### The objective cannot be found

Run:

```text
/scoreboard objectives list
```

Then make sure `objective-name` exactly matches the death objective on the server.

### The Discord leaderboard message was deleted

Set:

```yaml
message-id: ""
```

Save the config and run:

```text
/d2d reload
```

DeathsToDiscord will create a new leaderboard message and save the new message ID automatically.

### The webhook or Discord channel was changed

If the old `message-id` belongs to a message that the new webhook cannot edit, clear `message-id` and run `/d2d reload` to create a new message through the new webhook.

### Some players are not displayed

Check the following:

- The player has been tracked by the server.
- The configured scoreboard objective contains the expected score.
- `show-zero-deaths` is enabled if the player has zero deaths.
- `mode` is not set to `TOP` with a limit that excludes the player.
- The leaderboard has not reached the configured Discord content limit.

### The leaderboard says some players were omitted

The full leaderboard is larger than the configured Discord message limit. Increase `max-discord-content-characters` up to `2000`, use `TOP` mode, or reduce the number of players displayed.

## Support and Bug Reports

If you find a bug or have a feature request, open an issue on the [GitHub Issues](https://github.com/mccreeper1318/deathstodiscord/issues) page.

## License

DeathsToDiscord is released under the [MIT License](LICENSE).
