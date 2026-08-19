package com.pinnacle.deathstodiscord;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class DeathsToDiscordPlugin extends JavaPlugin implements Listener, TabExecutor {

    private static final int DISCORD_MAX_CONTENT_LENGTH = 2000;
    private static final int DEFAULT_DISCORD_CONTENT_LIMIT = 1900;
    private static final int MIN_DISCORD_CONTENT_LIMIT = 500;

    private final UpdateCycleState deathUpdateState = new UpdateCycleState();
    private HttpClient http;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("d2d") != null) {
            getCommand("d2d").setExecutor(this);
            getCommand("d2d").setTabCompleter(this);
        }

        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String webhookUrl = getConfig().getString("webhook-url", "");
        if (!isWebhookConfigured(webhookUrl)) {
            getLogger().warning("Webhook URL is not set! Set it in config.yml (webhook-url). Plugin will not post.");
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> updateDiscordLeaderboard(null, () -> { }));

        getLogger().info("DeathsToDiscord v" + getPluginMeta().getVersion() + " enabled. Updates will post on every death.");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (deathUpdateState.requestUpdate()) {
            scheduleDeathUpdate(getUpdateDelayTicks());
        }
    }

    private long getUpdateDelayTicks() {
        int delaySeconds = Math.max(0, getConfig().getInt("update-delay-seconds", 2));
        return delaySeconds * 20L;
    }

    private void scheduleDeathUpdate(long delayTicks) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            deathUpdateState.markUpdateStarted();
            updateDiscordLeaderboard(null, this::completeDeathUpdate);
        }, delayTicks);
    }

    private void completeDeathUpdate() {
        if (deathUpdateState.completeUpdateAndShouldScheduleAgain()) {
            scheduleDeathUpdate(getUpdateDelayTicks());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("d2d")) return false;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("d2d.admin")) {
                sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
                return true;
            }

            reloadConfig();
            sender.sendMessage(Component.text("DeathsToDiscord config reloaded. Updating Discord message...", NamedTextColor.GREEN));
            updateDiscordLeaderboard(sender, () -> { });
            return true;
        }

        sender.sendMessage(Component.text("Usage: /d2d reload", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("d2d")) return Collections.emptyList();
        if (args.length == 1) {
            return Collections.singletonList("reload").stream()
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void updateDiscordLeaderboard(CommandSender sender, Runnable onComplete) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(this, () -> updateDiscordLeaderboard(sender, onComplete));
            return;
        }

        String webhookUrl = getConfig().getString("webhook-url", "");
        if (!isWebhookConfigured(webhookUrl)) {
            completeWithFailure(sender, "Webhook URL is not set in config.yml.", onComplete);
            return;
        }

        String objectiveName = getConfig().getString("objective-name", "deaths");
        String content = buildLeaderboardMessage(objectiveName);
        if (content == null) {
            completeWithFailure(sender, "Objective '" + objectiveName + "' not found on main scoreboard.", onComplete);
            return;
        }

        String messageId = getConfig().getString("message-id", "");
        if (messageId == null || messageId.isBlank()) {
            createDiscordMessageThenPatch(webhookUrl, content, sender, onComplete);
            return;
        }

        patchDiscordMessageAsync(webhookUrl, messageId, content, sender, onComplete);
    }

    private String buildLeaderboardMessage(String objectiveName) {
        FileConfiguration cfg = getConfig();
        String mode = cfg.getString("mode", "ALL").trim().toUpperCase(Locale.ROOT);
        int top = Math.max(1, cfg.getInt("top", 10));
        boolean showZeroDeaths = cfg.getBoolean("show-zero-deaths", true);
        int maxContentChars = getMaxDiscordContentChars();

        Scoreboard main = Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();
        Objective obj = main.getObjective(objectiveName);
        if (obj == null) return null;

        Map<String, Integer> scores = new HashMap<>();

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            String name = player.getName();
            if (name == null || name.isBlank()) continue;

            Score score = obj.getScore(name);
            int deaths = score.isScoreSet() ? score.getScore() : 0;

            if (!showZeroDeaths && deaths == 0) continue;
            scores.put(name, deaths);
        }

        if (!Bukkit.getWorlds().isEmpty()) {
            World firstWorld = Bukkit.getWorlds().get(0);
            File playerDataDir = new File(firstWorld.getWorldFolder(), "playerdata");
            File[] files = playerDataDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".dat"));

            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    if (fileName.length() < 5) continue;

                    String uuidText = fileName.substring(0, fileName.length() - 4);

                    try {
                        UUID uuid = UUID.fromString(uuidText);
                        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                        String name = player.getName();

                        if (name == null || name.isBlank()) continue;
                        if (scores.containsKey(name)) continue;

                        Score score = obj.getScore(name);
                        int deaths = score.isScoreSet() ? score.getScore() : 0;

                        if (!showZeroDeaths && deaths == 0) continue;
                        scores.put(name, deaths);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        return LeaderboardFormatter.build(scores, mode, top, maxContentChars);
    }

    private int getMaxDiscordContentChars() {
        int configuredLimit = getConfig().getInt("max-discord-content-characters", DEFAULT_DISCORD_CONTENT_LIMIT);
        return Math.min(DISCORD_MAX_CONTENT_LENGTH, Math.max(MIN_DISCORD_CONTENT_LIMIT, configuredLimit));
    }

    private void createDiscordMessageThenPatch(String webhookUrl, String content, CommandSender sender, Runnable onComplete) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String createdJson = discordWebhookPostWait(webhookUrl, "**💀 Death Leaderboard**\nInitializing…");
                String createdMessageId = extractJsonStringField(createdJson, "id");

                if (createdMessageId == null || createdMessageId.isBlank()) {
                    throw new RuntimeException("Could not read message id from Discord response.");
                }

                Bukkit.getScheduler().runTask(this, () -> {
                    getConfig().set("message-id", createdMessageId);
                    saveConfig();
                    getLogger().info("Created Discord message. Saved message-id=" + createdMessageId);
                    patchDiscordMessageAsync(webhookUrl, createdMessageId, content, sender, onComplete);
                });
            } catch (Exception e) {
                completeWithFailure(sender, "Discord message creation failed: " + e.getMessage(), onComplete);
            }
        });
    }

    private void patchDiscordMessageAsync(String webhookUrl, String messageId, String content, CommandSender sender, Runnable onComplete) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                discordWebhookPatchMessage(webhookUrl, messageId, content);
                completeSuccessfully(sender, onComplete);
            } catch (Exception e) {
                completeWithFailure(sender, "Discord leaderboard update failed: " + e.getMessage(), onComplete);
            }
        });
    }

    private void completeSuccessfully(CommandSender sender, Runnable onComplete) {
        Bukkit.getScheduler().runTask(this, () -> {
            if (sender != null) {
                sender.sendMessage(Component.text("DeathsToDiscord Discord message updated.", NamedTextColor.GREEN));
            }
            onComplete.run();
        });
    }

    private void completeWithFailure(CommandSender sender, String message, Runnable onComplete) {
        Bukkit.getScheduler().runTask(this, () -> {
            if (sender != null) {
                sender.sendMessage(Component.text("Update failed: " + message, NamedTextColor.RED));
            }
            getLogger().warning(message);
            onComplete.run();
        });
    }

    private String discordWebhookPostWait(String webhookUrl, String content) throws Exception {
        String url = webhookUrl.contains("?") ? webhookUrl + "&wait=true" : webhookUrl + "?wait=true";
        String json = "{\"content\":\"" + escapeJson(trimHardLimit(content, DISCORD_MAX_CONTENT_LENGTH)) + "\"}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("Discord HTTP " + resp.statusCode() + " response: " + resp.body());
        }
        return resp.body();
    }

    private void discordWebhookPatchMessage(String webhookUrl, String messageId, String content) throws Exception {
        String patchUrl = removeQueryString(webhookUrl) + "/messages/" + messageId;
        String json = "{\"content\":\"" + escapeJson(trimHardLimit(content, DISCORD_MAX_CONTENT_LENGTH)) + "\"}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(patchUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("Discord HTTP " + resp.statusCode() + " response: " + resp.body());
        }
    }

    private boolean isWebhookConfigured(String webhookUrl) {
        return webhookUrl != null && !webhookUrl.isBlank() && !webhookUrl.contains("PASTE_WEBHOOK_URL_HERE");
    }

    private String removeQueryString(String webhookUrl) {
        int queryIndex = webhookUrl.indexOf('?');
        if (queryIndex == -1) return webhookUrl;
        return webhookUrl.substring(0, queryIndex);
    }

    private String trimHardLimit(String content, int maxContentChars) {
        int safeLimit = Math.min(DISCORD_MAX_CONTENT_LENGTH, Math.max(MIN_DISCORD_CONTENT_LIMIT, maxContentChars));
        if (content.length() <= safeLimit) return content;

        String suffix = "\n...trimmed to fit Discord's message limit.";
        int limitWithSuffix = Math.max(0, safeLimit - suffix.length());
        return content.substring(0, limitWithSuffix) + suffix;
    }

    private String escapeJson(String s) {
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private String extractJsonStringField(String json, String field) {
        if (json == null) return null;
        String needle = "\"" + field + "\":\"";
        int idx = json.indexOf(needle);
        if (idx == -1) return null;
        int start = idx + needle.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }
}
