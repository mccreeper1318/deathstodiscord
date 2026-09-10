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
        int rootIndent = findRootIndent(content);
        if (rootIndent < 0) {
            return false;
        }

        String quotedKey = Pattern.quote(key);
        Pattern keyPattern = Pattern.compile(
                "^(?:" + quotedKey + "|\\\"" + quotedKey + "\\\"|'" + quotedKey + "')\\s*:");

        for (String line : content.split("\\R", -1)) {
            int indent = leadingIndent(line);
            if (indent != rootIndent) {
                continue;
            }

            String trimmed = line.substring(indent);
            if (keyPattern.matcher(trimmed).find()) {
                return true;
            }
        }
        return false;
    }

    private static int findRootIndent(String content) {
        int rootIndent = Integer.MAX_VALUE;
        for (String line : content.split("\\R", -1)) {
            int indent = leadingIndent(line);
            String trimmed = line.substring(indent);
            if (trimmed.isBlank()
                    || trimmed.startsWith("#")
                    || isDocumentMarker(trimmed)
                    || trimmed.startsWith("%")) {
                continue;
            }
            rootIndent = Math.min(rootIndent, indent);
        }
        return rootIndent == Integer.MAX_VALUE ? -1 : rootIndent;
    }

    private static boolean isDocumentMarker(String trimmed) {
        if (!(trimmed.startsWith("---") || trimmed.startsWith("..."))) {
            return false;
        }
        if (trimmed.length() == 3) {
            return true;
        }

        String suffix = trimmed.substring(3);
        if (suffix.isBlank()) {
            return true;
        }
        if (!Character.isWhitespace(suffix.charAt(0))) {
            return false;
        }
        return suffix.stripLeading().startsWith("#");
    }

    private static int leadingIndent(String line) {
        int indent = 0;
        while (indent < line.length()) {
            char current = line.charAt(indent);
            if (current != ' ' && current != '\t') {
                break;
            }
            indent++;
        }
        return indent;
    }
}
