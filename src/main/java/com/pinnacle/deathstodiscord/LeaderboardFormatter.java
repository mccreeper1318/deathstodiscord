package com.pinnacle.deathstodiscord;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class LeaderboardFormatter {

    private LeaderboardFormatter() {
    }

    static String build(Map<String, Integer> scores, String mode, int top, int maxContentChars) {
        return build(scores, mode, top, maxContentChars, new Date());
    }

    static String build(Map<String, Integer> scores, String mode, int top, int maxContentChars, Date updatedAt) {
        String normalizedMode = mode == null ? "ALL" : mode.trim().toUpperCase(Locale.ROOT);
        int normalizedTop = Math.max(1, top);

        List<Map.Entry<String, Integer>> sorted = scores.entrySet().stream()
                .sorted((a, b) -> {
                    int scoreCompare = Integer.compare(b.getValue(), a.getValue());
                    if (scoreCompare != 0) return scoreCompare;
                    return a.getKey().compareToIgnoreCase(b.getKey());
                })
                .collect(Collectors.toList());

        if ("TOP".equals(normalizedMode)) {
            sorted = sorted.stream().limit(normalizedTop).toList();
        }

        String header = "TOP".equals(normalizedMode)
                ? "**💀 Death Leaderboard (Top " + normalizedTop + ")**\n"
                : "**💀 Death Leaderboard (Everyone)**\n";
        String footer = "\n_Tracked players: " + scores.size() + "_\n_Updated: " + updatedAt + "_";

        List<String> entryLines = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, Integer> entry : sorted) {
            entryLines.add(rank + ". " + entry.getKey() + " — " + entry.getValue() + "\n");
            rank++;
        }

        StringBuilder sb = new StringBuilder(header);
        if (entryLines.isEmpty()) {
            sb.append("_No tracked players found._\n");
        } else {
            appendLeaderboardLinesWithinLimit(sb, entryLines, footer, maxContentChars);
        }

        sb.append(footer);
        return trimToLimit(sb.toString(), maxContentChars);
    }

    private static void appendLeaderboardLinesWithinLimit(StringBuilder sb, List<String> entryLines, String footer, int maxContentChars) {
        for (int i = 0; i < entryLines.size(); i++) {
            String line = entryLines.get(i);
            int remainingAfterLine = entryLines.size() - i - 1;
            String omittedLine = remainingAfterLine > 0 ? "...and " + remainingAfterLine + " more players.\n" : "";

            if (sb.length() + line.length() + omittedLine.length() + footer.length() > maxContentChars) {
                int omitted = entryLines.size() - i;
                String trimLine = "...and " + omitted + " more players.\n";
                if (sb.length() + trimLine.length() + footer.length() <= maxContentChars) {
                    sb.append(trimLine);
                }
                return;
            }

            sb.append(line);
        }
    }

    private static String trimToLimit(String content, int maxContentChars) {
        if (content.length() <= maxContentChars) return content;

        String suffix = "\n...trimmed to fit Discord's message limit.";
        int limitWithSuffix = Math.max(0, maxContentChars - suffix.length());
        return content.substring(0, limitWithSuffix) + suffix;
    }
}
