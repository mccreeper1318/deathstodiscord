package com.pinnacle.deathstodiscord;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class DeathsToDiscordPlugin extends JavaPlugin implements Listener, TabExecutor {

    private final AtomicBoolean updateScheduled = new AtomicBoolean(false);
    private HttpClient http;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        // Register /d2d
        if (getCommand("d2d") != null) {
            getCommand("d2d").setExecutor(this);
            getCommand("d2d").setTabCompleter(this);
        }

        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String webhookUrl = getConfig().getString("webhook-url", "");
        if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.contains("PASTE_WEBHOOK_URL_HERE")) {
            getLogger().warning("Webhook URL is not set! Set it in config.yml (webhook-url). Plugin will not post.");
            return;
        }

        // Ensure message exists (create once, save message-id), then post an initial update
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                ensureDiscordMessageExists();
                patchLeaderboardNow();
            } catch (Exception e) {
                getLogger().warning("Startup Discord setup failed: " + e.getMessage());
            }
        });

        getLogger().info("DeathsToDiscord enabled. Updates will post on every death.");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        int delaySeconds = Math.max(0, getConfig().getInt("update-delay-seconds", 2));
        long delayTicks = delaySeconds * 20L;

        // Debounce: if multiple deaths happen quickly, do one patch shortly after
        if (updateScheduled.compareAndSet(false, true)) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
                try {
                    ensureDiscordMessageExists();
                    patchLeaderboardNow();
                } catch (Exception e) {
                    getLogger().warning("Failed to patch leaderboard: " + e.getMessage());
                } finally {
                    updateScheduled.set(false);
                }
            }, delayTicks);
        }
    }

    // ---------------- /d2d reload ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("d2d")) return false;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("d2d.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                return true;
            }

            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "DeathsToDiscord config reloaded. Updating Discord message...");

            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    ensureDiscordMessageExists();
                    patchLeaderboardNow();
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Update failed: " + e.getMessage());
                    getLogger().warning("Reload-triggered update failed: " + e.getMessage());
                }
            });

            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /d2d reload");
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

    // ---------------- Leaderboard build ----------------

    private String buildLeaderboardMessage(String objectiveName) {
        FileConfiguration cfg = getConfig();
        String mode = cfg.getString("mode", "ALL").trim().toUpperCase(Locale.ROOT);
        int top = Math.max(1, cfg.getInt("top", 10));

        Scoreboard main = Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();
        Objective obj = main.getObjective(objectiveName);
        if (obj == null) return null;

        Map<String, Integer> scores = new HashMap<>();
        for (String entry : main.getEntries()) {
            Score s = obj.getScore(entry);
            if (!s.isScoreSet()) continue;
            scores.put(entry, s.getScore());
        }

        List<Map.Entry<String, Integer>> sorted = scores.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());

        if ("TOP".equals(mode)) {
            sorted = sorted.stream().limit(top).toList();
        }

        StringBuilder sb = new StringBuilder();
        if ("TOP".equals(mode)) {
            sb.append("**💀 Death Leaderboard (Top ").append(top).append(")**\n");
        } else {
            sb.append("**💀 Death Leaderboard (Everyone)**\n");
        }

        int rank = 1;
        for (Map.Entry<String, Integer> e : sorted) {
            sb.append(rank).append(". ").append(e.getKey()).append(" — ").append(e.getValue()).append("\n");
            rank++;
        }

        sb.append("\n_Updated: ").append(new Date()).append("_");
        return sb.toString();
    }

    // ---------------- Discord message management ----------------

    private void ensureDiscordMessageExists() throws Exception {
        String webhookUrl = getConfig().getString("webhook-url", "");
        String messageId = getConfig().getString("message-id", "");

        if (messageId != null && !messageId.isBlank()) return;

        String content = "**💀 Death Leaderboard**\nInitializing…";
        String createdJson = discordWebhookPostWait(webhookUrl, content);

        String id = extractJsonStringField(createdJson, "id");
        if (id == null || id.isBlank()) {
            throw new RuntimeException("Could not read message id from Discord response.");
        }

        getConfig().set("message-id", id);
        saveConfig();

        getLogger().info("Created Discord message. Saved message-id=" + id);
    }

    private void patchLeaderboardNow() throws Exception {
        String webhookUrl = getConfig().getString("webhook-url", "");
        String messageId = getConfig().getString("message-id", "");
        String objectiveName = getConfig().getString("objective-name", "deaths");

        if (messageId == null || messageId.isBlank()) {
            throw new RuntimeException("message-id not set (should have been created automatically).");
        }

        String content = buildLeaderboardMessage(objectiveName);
        if (content == null) {
            throw new RuntimeException("Objective '" + objectiveName + "' not found on main scoreboard.");
        }

        discordWebhookPatchMessage(webhookUrl, messageId, content);
    }

    // ---------------- Discord Webhook HTTP (Java 21 HttpClient) ----------------

    private String discordWebhookPostWait(String webhookUrl, String content) throws Exception {
        String url = webhookUrl.contains("?") ? webhookUrl + "&wait=true" : webhookUrl + "?wait=true";
        String json = "{\"content\":\"" + escapeJson(content) + "\"}";

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
        String patchUrl = webhookUrl + "/messages/" + messageId;
        String json = "{\"content\":\"" + escapeJson(content) + "\"}";

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
