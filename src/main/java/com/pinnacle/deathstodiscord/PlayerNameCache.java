package com.pinnacle.deathstodiscord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PlayerNameCache {

    private final Map<UUID, String> namesByUuid = new LinkedHashMap<>();

    void remember(UUID playerUuid, String playerName) {
        if (playerUuid != null && playerName != null && !playerName.isBlank()) {
            namesByUuid.put(playerUuid, playerName);
        }
    }

    List<String> snapshot() {
        return List.copyOf(namesByUuid.values());
    }
}
