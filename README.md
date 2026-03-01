# DeathsToDiscord

A Paper 1.21.x plugin that syncs your Minecraft **`deaths` scoreboard
objective** to Discord using a webhook.

The plugin automatically: - Creates a Discord message (once) - Saves its
message ID - Edits (PATCHes) the same message - Updates on every player
death - Supports live config reload with `/d2d reload`

------------------------------------------------------------------------

# Features

-   Uses your existing `/scoreboard objectives add deaths deathCount`
-   Supports:
    -   `ALL` mode (everyone listed)
    -   `TOP` mode (top N players only)
-   Offline players included (reads scoreboard directly)
-   No Discord bot required (webhook only)
-   Automatically patches one message (no spam)
-   Debounces rapid deaths to avoid rate limits
-   Live reload command

------------------------------------------------------------------------

# Requirements

-   Paper 1.21.x
-   Java 21 (required for PATCH support via HttpClient)
-   A Discord server with webhook access

------------------------------------------------------------------------

# Discord Setup (Webhook)

1.  Go to your Discord server
2.  Edit the channel where you want the leaderboard
3.  Integrations → Webhooks → New Webhook
4.  Copy the Webhook URL
5.  Paste it into the plugin config (see below)

Keep the webhook URL private.

------------------------------------------------------------------------

# Installation

1.  Stop your Minecraft server

2.  Place the jar in:

        /plugins/

3.  Start the server

4.  The plugin will generate:

        plugins/DeathsToDiscord/config.yml

5.  Stop the server

6.  Open `config.yml` and paste your webhook URL

7.  Start the server again

The plugin will: - Create the Discord message automatically - Store the
message ID - Begin updating on deaths

------------------------------------------------------------------------

# Configuration

Located at:

    plugins/DeathsToDiscord/config.yml

``` yaml
webhook-url: "PASTE_WEBHOOK_URL_HERE"

objective-name: "deaths"

mode: "ALL"     # ALL or TOP
top: 10         # Used only if mode = TOP

message-id: ""  # Auto-filled after first run

update-delay-seconds: 2
```

## Config Options Explained

### webhook-url

Your Discord webhook URL.

### objective-name

Must match exactly what `/scoreboard objectives list` shows.

### mode

-   `"ALL"` → shows everyone
-   `"TOP"` → shows only the top players

### top

Number of players shown in TOP mode.

### message-id

Automatically filled after first startup.\
Do not manually edit unless you delete the Discord message.

### update-delay-seconds

Small buffer to batch rapid deaths and prevent Discord rate limits.

------------------------------------------------------------------------

# Commands

## `/d2d reload`

-   Reloads `config.yml`

-   Forces an immediate Discord update

-   Requires permission:

        d2d.admin

-   Default: OP only

------------------------------------------------------------------------

# How It Works

1.  Reads the main scoreboard
2.  Pulls all scores from objective `deaths`
3.  Sorts players by death count
4.  Builds a formatted leaderboard
5.  PATCHes the same Discord message

No bot account required.

------------------------------------------------------------------------

# Troubleshooting

## "Webhook URL is not set"

Paste your webhook URL into config.yml and reload.

## "Objective 'deaths' not found"

Run:

    /scoreboard objectives list

Make sure the objective name matches exactly.

## "Discord HTTP 401"

Webhook URL is invalid or deleted.

## "Discord HTTP 404"

Message ID was deleted --- clear `message-id` in config and reload.

## "Invalid HTTP method: PATCH"

Make sure you are: - Using Java 21 - Running the latest plugin version

------------------------------------------------------------------------

# Notes

-   Includes offline players as long as they exist in the scoreboard.
-   Does not create or modify your scoreboard.
-   Safe to reload with `/d2d reload`.
-   Designed for long-running season leaderboards.
