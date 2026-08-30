package com.pinnacle.deathstodiscord;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

final class KnownPlayerDirectory {

    private static final int PROFILE_LOOKUPS_PER_TICK = 50;

    private final DeathsToDiscordPlugin plugin;
    private final PlayerNameCache playerNames = new PlayerNameCache();

    KnownPlayerDirectory(DeathsToDiscordPlugin plugin) {
        this.plugin = plugin;
    }

    void remember(UUID playerUuid, String playerName) {
        playerNames.remember(playerUuid, playerName);
    }

    List<String> snapshot() {
        return playerNames.snapshot();
    }

    void discoverHistoricalPlayers(Runnable onComplete) {
        Bukkit.getOnlinePlayers().forEach(player -> remember(player.getUniqueId(), player.getName()));
        List<UUID> bukkitKnownUuids = Arrays.stream(Bukkit.getOfflinePlayers())
                .map(OfflinePlayer::getUniqueId)
                .toList();

        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            resolveNamesOverMultipleTicks(bukkitKnownUuids, onComplete);
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

            List<UUID> discoveredUuids = mergeHistoricalPlayerUuids(bukkitKnownUuids, playerUuids);
            Bukkit.getScheduler().runTask(plugin, () -> resolveNamesOverMultipleTicks(discoveredUuids, onComplete));
        });
    }

    static List<UUID> mergeHistoricalPlayerUuids(List<UUID> bukkitKnownUuids,
                                                  List<UUID> playerDataUuids) {
        LinkedHashSet<UUID> mergedUuids = new LinkedHashSet<>(bukkitKnownUuids);
        mergedUuids.addAll(playerDataUuids);
        return List.copyOf(mergedUuids);
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
                    UUID playerUuid = remaining.removeFirst();
                    OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
                    remember(playerUuid, player.getName());
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
