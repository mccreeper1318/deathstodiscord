package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginSettingsTest {

    @Test
    void acceptsValidSettingsWithoutNormalizingThemSilently() {
        PluginSettings.LoadResult result = validSettings("TOP", 5, 0, 2000);

        assertTrue(result.valid());
        assertEquals("TOP", result.settings().mode());
        assertEquals(5, result.settings().top());
        assertEquals(2000, result.settings().maxDiscordContentCharacters());
    }

    @Test
    void reportsEveryInvalidSetting() {
        PluginSettings.LoadResult result = PluginSettings.validate(
                "not a url", "", "SIDEWAYS", -4, "yes", -1, 2500);

        assertFalse(result.valid());
        assertEquals(7, result.errors().size());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("mode")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("top")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("update-delay-seconds")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("max-discord")));
    }

    @Test
    void rejectsWebhookUrlsContainingFragments() {
        PluginSettings.LoadResult result = PluginSettings.validate(
                "https://discord.com/api/webhooks/123/token#fragment",
                "deaths",
                "ALL",
                10,
                true,
                2,
                1900);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("webhook-url")));
    }

    @Test
    void usesThePriorContentLimitDefaultWhenLegacyConfigOmitsTheSetting() {
        PluginSettings.LoadResult result = PluginSettings.validate(
                "https://discord.com/api/webhooks/123/token",
                "deaths",
                "ALL",
                10,
                true,
                2,
                null);

        assertTrue(result.valid());
        assertEquals(1900, result.settings().maxDiscordContentCharacters());
    }

    @Test
    void stillRejectsAnExplicitlyInvalidContentLimit() {
        PluginSettings.LoadResult result = PluginSettings.validate(
                "https://discord.com/api/webhooks/123/token",
                "deaths",
                "ALL",
                10,
                true,
                2,
                "not-a-number");

        assertFalse(result.valid());
    }

    private static PluginSettings.LoadResult validSettings(String mode, int top, int delay, int limit) {
        return PluginSettings.validate(
                "https://discord.com/api/webhooks/123/token",
                "deaths",
                mode,
                top,
                true,
                delay,
                limit);
    }
}
