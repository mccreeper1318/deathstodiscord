package com.pinnacle.deathstodiscord;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

final class KnownPlayerDirectory {

    private static final int PROFILE_LOOKUPS_PER_TICK = 50;

    private final DeathsToDiscordPlugin plugin;
    private final Set<String> playerNames = new LinkedHashSet<>();

    KnownPlayerDirectory(DeathsToDiscordPlugin plugin) {
        this.plugin = plugin;
    }

    void remember(String playerName) {
        if (playerName != null && !playerName.isBlank()) {
            playerNames.add(playerName);
        }
    }

    List<String> snapshot() {
        return List.copyOf(playerNames);
    }

    void discoverHistoricalPlayers(Runnable onComplete) {
        Bukkit.getOnlinePlayers().forEach(player -> remember(player.getName()));

        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            onComplete.run();
            return;
        }

        Path playerDataDirectory = worlds.get(0).getWorldFolder().toPath().resolve("playerdata");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<UUID> playerUuids;
            try (Stream<Path> files = Files.isDirectory(playerDataDirectory)
                    ? Files.list(playerDataDirectory)
                    : Stream.empty()) {
                playerUuids = PlayerDataFileParser.parseUuids(
                        files.map(path -> path.getFileName().toString()).toList());
            } catch (IOException e) {
                plugin.getLogger().warning("Could not scan historical player data: " + e.getMessage());
                playerUuids = List.of();
            }

            List<UUID> discoveredUuids = playerUuids;
            Bukkit.getScheduler().runTask(plugin, () -> resolveNamesOverMultipleTicks(discoveredUuids, onComplete));
        });
    }

    private void resolveNamesOverMultipleTicks(List<UUID> uuids, Runnable onComplete) {
        if (uuids.isEmpty()) {
            onComplete.run();
            return;
        }

        Deque<UUID> remaining = new ArrayDeque<>(uuids);
        new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (processed < PROFILE_LOOKUPS_PER_TICK && !remaining.isEmpty()) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(remaining.removeFirst());
                    remember(player.getName());
                    processed++;
                }

                if (remaining.isEmpty()) {
                    cancel();
                    onComplete.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
