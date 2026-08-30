package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerNameCacheTest {

    @Test
    void aJoinWithANewNameReplacesTheSamePlayersCachedName() {
        PlayerNameCache cache = new PlayerNameCache();
        UUID playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        cache.remember(playerUuid, "OldName");
        cache.remember(playerUuid, "NewName");

        assertEquals(List.of("NewName"), cache.snapshot());
    }

    @Test
    void differentPlayersRemainSeparate() {
        PlayerNameCache cache = new PlayerNameCache();
        cache.remember(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), "First");
        cache.remember(UUID.fromString("123e4567-e89b-12d3-a456-426614174001"), "Second");

        assertEquals(List.of("First", "Second"), cache.snapshot());
    }
}
