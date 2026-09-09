package com.pinnacle.deathstodiscord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

final class ConfigKeyPresence {

    private ConfigKeyPresence() {
    }

    static boolean containsTopLevelKey(Path configFile, String key) throws IOException {
        return containsTopLevelKey(Files.readString(configFile, StandardCharsets.UTF_8), key);
    }

    static boolean containsTopLevelKey(String yaml, String key) {
        if (yaml == null || yaml.isEmpty()) {
            return false;
        }

        String content = yaml.charAt(0) == '\uFEFF' ? yaml.substring(1) : yaml;
        String quotedKey = Pattern.quote(key);
        Pattern keyPattern = Pattern.compile(
                "(?m)^(?:" + quotedKey + "|\\\"" + quotedKey + "\\\"|'" + quotedKey + "')\\s*:");
        return keyPattern.matcher(content).find();
    }
}
