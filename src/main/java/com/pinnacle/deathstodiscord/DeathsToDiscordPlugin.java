package com.pinnacle.deathstodiscord;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.net.http.HttpClient;
import java.time.Duration;

public class DeathsToDiscordPlugin extends org.bukkit.plugin.java.JavaPlugin {

    private final UpdateCycleState deathUpdateState = new UpdateCycleState();
    private final OrderedSnapshotDispatcher snapshotDispatcher = new OrderedSnapshotDispatcher();

    private KnownPlayerDirectory playerDirectory;
    private LeaderboardSnapshotService leaderboardSnapshots;
    private DiscordUpdateService discordUpdates;
    private PluginSettings settings;
    private long configurationGeneration;
    private long lifecycleGeneration;
    private boolean shuttingDown;

    @Override
    public void onEnable() {
        lifecycleGeneration++;
        shuttingDown = false;
        deathUpdateState.reset();
        snapshotDispatcher.reset();
        saveDefaultConfig();

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        DiscordMessageStateStore stateStore = new DiscordMessageStateStore(getDataFolder(), getLogger());
        discordUpdates = new DiscordUpdateService(this, new DiscordWebhookClient(http), stateStore);
        playerDirectory = new KnownPlayerDirectory(this);
        leaderboardSnapshots = new LeaderboardSnapshotService(this, playerDirectory);

        Bukkit.getPluginManager().registerEvents(
                new PlayerActivityListener(playerDirectory, this::onPlayerDeath), this);

        D2dCommand commandHandler = new D2dCommand(this::reloadPlugin);
        if (getCommand("d2d") != null) {
            getCommand("d2d").setExecutor(commandHandler);
            getCommand("d2d").setTabCompleter(commandHandler);
        }

        loadAndApplySettings(null, false);
        playerDirectory.discoverHistoricalPlayers(() -> {
            if (settings != null && settings.webhookConfigured()) {
                updateDiscordLeaderboard(null, () -> { });
            }
        });

        getLogger().info("DeathsToDiscord v" + getPluginMeta().getVersion()
                + " enabled. Updates will post on every death.");
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        if (discordUpdates != null) {
            discordUpdates.shutdown();
        }
    }

    private void onPlayerDeath() {
        if (shuttingDown || settings == null || !settings.webhookConfigured()) {
            return;
        }
        if (deathUpdateState.requestUpdate()) {
            scheduleDeathUpdate(settings.updateDelayTicks());
        }
    }

    private void scheduleDeathUpdate(long delayTicks) {
        long scheduledLifecycle = lifecycleGeneration;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (shuttingDown || scheduledLifecycle != lifecycleGeneration) {
                return;
            }
            deathUpdateState.markUpdateStarted();
            updateDiscordLeaderboard(null, () -> completeDeathUpdate(scheduledLifecycle));
        }, delayTicks);
    }

    private void completeDeathUpdate(long completedLifecycle) {
        if (shuttingDown || completedLifecycle != lifecycleGeneration) {
            return;
        }
        if (deathUpdateState.completeUpdateAndShouldScheduleAgain()) {
            PluginSettings current = settings;
            if (current != null && current.webhookConfigured()) {
                scheduleDeathUpdate(current.updateDelayTicks());
            } else {
                deathUpdateState.discardScheduledUpdate();
            }
        }
    }

    private void reloadPlugin(CommandSender sender) {
        reloadConfig();
        if (!loadAndApplySettings(sender, true)) {
            return;
        }

        sender.sendMessage(Component.text(
                "DeathsToDiscord config reloaded. Updating Discord message...", NamedTextColor.GREEN));
        updateDiscordLeaderboard(sender, () -> { });
    }

    private boolean loadAndApplySettings(CommandSender sender, boolean preservePreviousOnFailure) {
        PluginSettings.LoadResult result = PluginSettings.validate(
                getConfig().getString("webhook-url"),
                getConfig().getString("objective-name"),
                getConfig().get("mode"),
                getConfig().get("top"),
                getConfig().get("show-zero-deaths"),
                getConfig().get("update-delay-seconds"),
                getConfig().get("max-discord-content-characters"));
        if (!result.valid()) {
            for (String error : result.errors()) {
                getLogger().severe("Invalid configuration: " + error);
                if (sender != null) {
                    sender.sendMessage(Component.text("Invalid configuration: " + error, NamedTextColor.RED));
                }
            }
            if (!preservePreviousOnFailure) {
                settings = null;
                discordUpdates.configureWebhook("");
            }
            return false;
        }

        PluginSettings loaded = result.settings();
        String fingerprint = discordUpdates.configureWebhook(loaded.webhookUrl());
        migrateLegacyMessageId(fingerprint);
        settings = loaded;
        configurationGeneration++;

        if (!loaded.webhookConfigured()) {
            getLogger().warning(
                    "Webhook URL is not set! Set it in config.yml (webhook-url). Plugin will not post.");
        }
        return true;
    }

    private void migrateLegacyMessageId(String fingerprint) {
        if (!getConfig().contains("message-id")) {
            return;
        }

        String legacyMessageId = getConfig().getString("message-id", "");
        if (legacyMessageId != null && !legacyMessageId.isBlank() && fingerprint.isBlank()) {
            getLogger().info(
                    "Legacy Discord message id will remain in config.yml until a webhook is configured.");
            return;
        }
        if (!discordUpdates.migrateLegacyMessageId(fingerprint, legacyMessageId)) {
            getLogger().severe(
                    "Could not migrate the legacy Discord message id to state.yml; config.yml was left unchanged.");
            return;
        }
        getConfig().set("message-id", null);
        saveConfig();
        if (legacyMessageId != null && !legacyMessageId.isBlank() && !fingerprint.isBlank()) {
            getLogger().info("Migrated the legacy Discord message id from config.yml to state.yml.");
        }
    }

    private void updateDiscordLeaderboard(CommandSender sender, Runnable onComplete) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(this, () -> updateDiscordLeaderboard(sender, onComplete));
            return;
        }

        PluginSettings capturedSettings = settings;
        long capturedGeneration = configurationGeneration;
        if (capturedSettings == null || !capturedSettings.webhookConfigured()) {
            completeWithFailure(sender, "Webhook URL is not set in config.yml.", onComplete);
            return;
        }

        OrderedSnapshotDispatcher.Reservation reservation = snapshotDispatcher.reserve();
        try {
            leaderboardSnapshots.build(capturedSettings, result -> {
                snapshotDispatcher.complete(reservation, () -> dispatchCompletedSnapshot(
                        capturedSettings, capturedGeneration, sender, onComplete, result));
            });
        } catch (RuntimeException error) {
            LeaderboardSnapshotService.Result failure = LeaderboardSnapshotService.Result.failure(
                    "Leaderboard snapshot collection failed.");
            snapshotDispatcher.complete(reservation, () -> dispatchCompletedSnapshot(
                    capturedSettings, capturedGeneration, sender, onComplete, failure));
        }
    }

    private void dispatchCompletedSnapshot(PluginSettings capturedSettings, long capturedGeneration,
                                           CommandSender sender, Runnable onComplete,
                                           LeaderboardSnapshotService.Result result) {
        if (capturedGeneration != configurationGeneration || settings != capturedSettings) {
            onComplete.run();
            return;
        }
        if (!result.successful()) {
            completeWithFailure(sender, result.failure(), onComplete);
            return;
        }
        discordUpdates.submit(capturedSettings.webhookUrl(), result.content(), sender, onComplete);
    }

    private void completeWithFailure(CommandSender sender, String message, Runnable onComplete) {
        if (sender != null) {
            sender.sendMessage(Component.text("Update failed: " + message, NamedTextColor.RED));
        }
        getLogger().warning(message);
        onComplete.run();
    }
}
