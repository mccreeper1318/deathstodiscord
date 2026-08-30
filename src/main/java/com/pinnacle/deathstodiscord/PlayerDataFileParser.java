package com.pinnacle.deathstodiscord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class PlayerDataFileParser {

    private PlayerDataFileParser() {
    }

    static List<UUID> parseUuids(Collection<String> fileNames) {
        List<UUID> result = new ArrayList<>();
        for (String fileName : fileNames) {
            if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".dat")) {
                continue;
            }
            String uuidText = fileName.substring(0, fileName.length() - 4);
            try {
                result.add(UUID.fromString(uuidText));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }
}
