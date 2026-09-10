package com.pinnacle.deathstodiscord;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
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
        List<UUID> bukkitKnownUuids = new ArrayList<>();

        try {
            Bukkit.getOnlinePlayers().forEach(player -> {
                try {
                    remember(player.getUniqueId(), player.getName());
                } catch (RuntimeException error) {
                    logDiscoveryFailure("Could not cache an online player during historical-player discovery", error);
                }
            });
        } catch (RuntimeException error) {
            logDiscoveryFailure("Could not enumerate online players during historical-player discovery", error);
        }

        try {
            bukkitKnownUuids.addAll(Arrays.stream(Bukkit.getOfflinePlayers())
                    .map(OfflinePlayer::getUniqueId)
                    .toList());
        } catch (RuntimeException error) {
            logDiscoveryFailure("Could not enumerate Bukkit's historical players", error);
        }

        List<World> worlds;
        try {
            worlds = Bukkit.getWorlds();
        } catch (RuntimeException error) {
            logDiscoveryFailure("Could not enumerate worlds for historical-player discovery", error);
            resolveNamesOverMultipleTicks(bukkitKnownUuids, onComplete);
            return;
        }

        if (worlds.isEmpty()) {
            resolveNamesOverMultipleTicks(bukkitKnownUuids, onComplete);
            return;
        }

        Path playerDataDirectory;
        try {
            playerDataDirectory = worlds.get(0).getWorldFolder().toPath().resolve("playerdata");
        } catch (RuntimeException error) {
            logDiscoveryFailure("Could not locate the historical player-data directory", error);
            resolveNamesOverMultipleTicks(bukkitKnownUuids, onComplete);
            return;
        }

        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<UUID> playerUuids = safelyScanPlayerData(
                        () -> {
                            try (Stream<Path> files = Files.isDirectory(playerDataDirectory)
                                    ? Files.list(playerDataDirectory)
                                    : Stream.empty()) {
                                return PlayerDataFileParser.parseUuids(
                                        files.map(path -> path.getFileName().toString()).toList());
                            }
                        },
                        error -> logDiscoveryFailure("Could not scan historical player data", error));

                List<UUID> discoveredUuids = mergeHistoricalPlayerUuids(bukkitKnownUuids, playerUuids);
                try {
                    Bukkit.getScheduler().runTask(
                            plugin, () -> resolveNamesOverMultipleTicks(discoveredUuids, onComplete));
                } catch (RuntimeException error) {
                    logDiscoveryFailure("Could not schedule historical-player profile resolution", error);
                    onComplete.run();
                }
            });
        } catch (RuntimeException error) {
            logDiscoveryFailure("Could not schedule historical player-data scanning", error);
            resolveNamesOverMultipleTicks(bukkitKnownUuids, onComplete);
        }
    }

    static List<UUID> mergeHistoricalPlayerUuids(List<UUID> bukkitKnownUuids,
                                                  List<UUID> playerDataUuids) {
        LinkedHashSet<UUID> mergedUuids = new LinkedHashSet<>(bukkitKnownUuids);
        mergedUuids.addAll(playerDataUuids);
        return List.copyOf(mergedUuids);
    }

    static List<UUID> safelyScanPlayerData(PlayerDataScan scan, Consumer<Exception> onFailure) {
        try {
            return scan.scan();
        } catch (IOException | RuntimeException error) {
            onFailure.accept(error);
            return List.of();
        }
    }

    static boolean resolveNamesBatch(Deque<UUID> remaining, int limit,
                                     Function<UUID, String> resolveName,
                                     BiConsumer<UUID, String> rememberName,
                                     Consumer<RuntimeException> onFailure) {
        int processed = 0;
        while (processed < limit && !remaining.isEmpty()) {
            UUID playerUuid = remaining.removeFirst();
            try {
                rememberName.accept(playerUuid, resolveName.apply(playerUuid));
            } catch (RuntimeException error) {
                onFailure.accept(error);
            }
            processed++;
        }
        return remaining.isEmpty();
    }

    private void resolveNamesOverMultipleTicks(List<UUID> uuids, Runnable onComplete) {
        if (uuids.isEmpty()) {
            onComplete.run();
            return;
        }

        Deque<UUID> remaining = new ArrayDeque<>(uuids);
        BukkitRunnable resolver = new BukkitRunnable() {
            @Override
            public void run() {
                boolean complete = resolveNamesBatch(
                        remaining,
                        PROFILE_LOOKUPS_PER_TICK,
                        playerUuid -> Bukkit.getOfflinePlayer(playerUuid).getName(),
                        KnownPlayerDirectory.this::remember,
                        error -> logDiscoveryFailure("Could not resolve a historical player profile", error));

                if (complete) {
                    try {
                        cancel();
                    } finally {
                        onComplete.run();
                    }
                }
            }
        };

        try {
            resolver.runTaskTimer(plugin, 0L, 1L);
        } catch (RuntimeException error) {
            logDiscoveryFailure("Could not schedule historical-player profile resolution", error);
            onComplete.run();
        }
    }

    private void logDiscoveryFailure(String context, Exception error) {
        String detail = error.getMessage();
        plugin.getLogger().warning(context + (detail == null || detail.isBlank() ? "." : ": " + detail));
    }

    @FunctionalInterface
    interface PlayerDataScan {
        List<UUID> scan() throws IOException;
    }
}
