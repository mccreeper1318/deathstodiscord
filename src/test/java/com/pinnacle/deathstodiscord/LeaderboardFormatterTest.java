package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardFormatterTest {

    @Test
    void sortsByDeathsDescendingThenNameAlphabetically() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("Zulu", 2);
        scores.put("beta", 7);
        scores.put("Alpha", 7);

        String message = LeaderboardFormatter.build(scores, "ALL", 10, 1900, new Date(0));

        int alpha = message.indexOf("1. Alpha — 7");
        int beta = message.indexOf("2. beta — 7");
        int zulu = message.indexOf("3. Zulu — 2");

        assertTrue(alpha >= 0);
        assertTrue(beta > alpha);
        assertTrue(zulu > beta);
    }

    @Test
    void topModeLimitsDisplayedPlayersWithoutChangingTrackedCount() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("One", 10);
        scores.put("Two", 8);
        scores.put("Three", 6);

        String message = LeaderboardFormatter.build(scores, "TOP", 2, 1900, new Date(0));

        assertTrue(message.contains("Death Leaderboard (Top 2)"));
        assertTrue(message.contains("1. One — 10"));
        assertTrue(message.contains("2. Two — 8"));
        assertFalse(message.contains("Three — 6"));
        assertTrue(message.contains("Tracked players: 3"));
    }

    @Test
    void trimsLargeLeaderboardsToConfiguredDiscordLimit() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            scores.put("Player" + String.format("%03d", i), 100 - i);
        }

        String message = LeaderboardFormatter.build(scores, "ALL", 10, 500, new Date(0));

        assertTrue(message.length() <= 500);
        assertTrue(message.contains("more players"));
        assertTrue(message.contains("Tracked players: 100"));
    }
}
