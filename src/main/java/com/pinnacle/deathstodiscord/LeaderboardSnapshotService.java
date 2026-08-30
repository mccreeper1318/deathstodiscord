package com.pinnacle.deathstodiscord;

import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

final class LeaderboardSnapshotService {

    private static final int SCORE_LOOKUPS_PER_TICK = 100;

    private final DeathsToDiscordPlugin plugin;
    private final KnownPlayerDirectory players;

    LeaderboardSnapshotService(DeathsToDiscordPlugin plugin, KnownPlayerDirectory players) {
        this.plugin = plugin;
        this.players = players;
    }

    void build(PluginSettings settings, Consumer<Result> callback) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> build(settings, callback));
            return;
        }

        Scoreboard main = Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();
        Objective objective = main.getObjective(settings.objectiveName());
        if (objective == null) {
            callback.accept(Result.failure(
                    "Objective '" + settings.objectiveName() + "' not found on main scoreboard."));
            return;
        }

        List<String> names = players.snapshot();
        Map<String, Integer> scores = new HashMap<>();
        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                int end = Math.min(names.size(), index + SCORE_LOOKUPS_PER_TICK);
                while (index < end) {
                    String name = names.get(index++);
                    Score score = objective.getScore(name);
                    int deaths = score.isScoreSet() ? score.getScore() : 0;
                    if (settings.showZeroDeaths() || deaths != 0) {
                        scores.put(name, deaths);
                    }
                }

                if (index >= names.size()) {
                    cancel();
                    formatAsynchronously(settings, Map.copyOf(scores), callback);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void formatAsynchronously(PluginSettings settings, Map<String, Integer> scores,
                                      Consumer<Result> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String content = LeaderboardFormatter.build(
                    scores,
                    settings.mode(),
                    settings.top(),
                    settings.maxDiscordContentCharacters(),
                    Instant.now());
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(Result.success(content)));
        });
    }

    record Result(String content, String failure) {
        static Result success(String content) {
            return new Result(content, null);
        }

        static Result failure(String failure) {
            return new Result(null, failure);
        }

        boolean successful() {
            return failure == null;
        }
    }
}
