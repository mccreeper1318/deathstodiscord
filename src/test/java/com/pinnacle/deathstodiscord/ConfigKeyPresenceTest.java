package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigKeyPresenceTest {

    private static final String KEY = "max-discord-content-characters";

    @Test
    void detectsExplicitNullTopLevelKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "webhook-url: \"example\"\nmax-discord-content-characters:\n", KEY));
    }

    @Test
    void detectsUniformlyIndentedTopLevelKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "  webhook-url: \"example\"\n  max-discord-content-characters: null\n", KEY));
    }

    @Test
    void detectsUniformlyIndentedQuotedTopLevelKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "    webhook-url: \"example\"\n    \"max-discord-content-characters\": null\n", KEY));
    }

    @Test
    void detectsTopLevelKeyWithValue() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "max-discord-content-characters: 1900\n", KEY));
    }

    @Test
    void treatsOmittedLegacyKeyAsAbsent() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "webhook-url: \"example\"\nobjective-name: deaths\n", KEY));
    }

    @Test
    void ignoresCommentedOrNestedKeys() {
        String yaml = "# max-discord-content-characters:\n"
                + "nested:\n"
                + "  max-discord-content-characters:\n";

        assertFalse(ConfigKeyPresence.containsTopLevelKey(yaml, KEY));
    }

    @Test
    void ignoresNestedKeyWhenRootMappingIsIndented() {
        String yaml = "  nested:\n"
                + "    max-discord-content-characters: null\n"
                + "  objective-name: deaths\n";

        assertFalse(ConfigKeyPresence.containsTopLevelKey(yaml, KEY));
    }

    @Test
    void documentMarkersDoNotChangeIndentedRootLevel() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "---\n  webhook-url: \"example\"\n  max-discord-content-characters: null\n...\n", KEY));
    }

    @Test
    void documentMarkersWithInlineCommentsDoNotChangeIndentedRootLevel() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "--- # config\n  webhook-url: \"example\"\n  max-discord-content-characters: null\n... # end\n", KEY));
    }

    @Test
    void markerLikeRootContentStillDeterminesRootIndent() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "---not-a-document-marker: true\n  max-discord-content-characters: null\n", KEY));
    }

    @Test
    void detectsQuotedTopLevelKeysAndUtf8Bom() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "\uFEFF\"max-discord-content-characters\": null\n", KEY));
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "'max-discord-content-characters': null\n", KEY));
    }
}
