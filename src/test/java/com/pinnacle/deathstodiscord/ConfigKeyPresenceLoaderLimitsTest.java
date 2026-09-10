package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigKeyPresenceLoaderLimitsTest {

    private static final String KEY = "max-discord-content-characters";

    @Test
    void acceptsBukkitScaleCodePointCountsWhenCheckingPresence() {
        StringBuilder yaml = new StringBuilder(3_200_100);
        yaml.append("padding: \"");
        yaml.append("a".repeat(3_200_000));
        yaml.append("\"\nmax-discord-content-characters: null\n");

        assertTrue(ConfigKeyPresence.containsTopLevelKey(yaml.toString(), KEY));
    }

    @Test
    void acceptsBukkitNestingDepthWhenCheckingPresence() {
        StringBuilder yaml = new StringBuilder();
        for (int depth = 0; depth < 60; depth++) {
            yaml.append("  ".repeat(depth)).append("level-").append(depth).append(":\n");
        }
        yaml.append("  ".repeat(60)).append("value: true\n");
        yaml.append("max-discord-content-characters: null\n");

        assertTrue(ConfigKeyPresence.containsTopLevelKey(yaml.toString(), KEY));
    }
}
