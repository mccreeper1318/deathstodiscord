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
    void acceptsDiscordWebhookUrlsWithSupportedHostsAndQueries() {
        PluginSettings.LoadResult result = PluginSettings.validate(
                "https://canary.discord.com/api/v10/webhooks/123/token?thread_id=456&wait=false",
                "deaths",
                "ALL",
                10,
                true,
                2,
                1900,
                true);

        assertTrue(result.valid());
    }

    @Test
    void reportsEveryInvalidSetting() {
        PluginSettings.LoadResult result = PluginSettings.validate(
                "not a url", "", "SIDEWAYS", -4, "yes", -1, 2500, true);

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
                1900,
                true);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("webhook-url")));
    }

    @Test
    void rejectsNonDiscordHostsEvenWhenThePathLooksLikeAWebhook() {
        PluginSettings.LoadResult result = PluginSettings.validate(
                "https://example.com/api/webhooks/123/token",
                "deaths",
                "ALL",
                10,
                true,
                2,
                1900,
                true);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("webhook-url")));
    }

    @Test
    void rejectsDiscordUrlsThatAreNotWebhookEndpoints() {
        PluginSettings.LoadResult result = PluginSettings.validate(
                "https://discord.com/channels/123/456",
                "deaths",
                "ALL",
                10,
                true,
                2,
                1900,
                true);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("webhook-url")));
    }

    @Test
    void rejectsInsecureDiscordWebhookUrls() {
        PluginSettings.LoadResult result = PluginSettings.validate(
                "http://discord.com/api/webhooks/123/token",
                "deaths",
                "ALL",
                10,
                true,
                2,
                1900,
                true);

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
                null,
                false);

        assertTrue(result.valid());
        assertEquals(1900, result.settings().maxDiscordContentCharacters());
    }

    @Test
    void honorsLoadedContentLimitWhenRawPresenceScanMissesYamlSyntax() {
        PluginSettings.LoadResult result = PluginSettings.validate(
                "https://discord.com/api/webhooks/123/token",
                "deaths",
                "ALL",
                10,
                true,
                2,
                500,
                false);

        assertTrue(result.valid());
        assertEquals(500, result.settings().maxDiscordContentCharacters());
    }

    @Test
    void rejectsLoadedInvalidContentLimitWhenRawPresenceScanMissesYamlSyntax() {
        PluginSettings.LoadResult result = PluginSettings.validate(
                "https://discord.com/api/webhooks/123/token",
                "deaths",
                "ALL",
                10,
                true,
                2,
                "not-a-number",
                false);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("max-discord")));
    }

    @Test
    void rejectsExplicitNullContentLimit() {
        PluginSettings.LoadResult result = PluginSettings.validate(
                "https://discord.com/api/webhooks/123/token",
                "deaths",
                "ALL",
                10,
                true,
                2,
                null,
                true);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("max-discord")));
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
                "not-a-number",
                true);

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
                limit,
                true);
    }
}
