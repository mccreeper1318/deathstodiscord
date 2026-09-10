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
        if (containsRootFlowMappingKey(content, key)) {
            return true;
        }

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
            if (keyPattern.matcher(trimmed).find() || isExplicitKeyLine(trimmed, key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRootFlowMappingKey(String content, String key) {
        int rootStart = firstMeaningfulContentStart(content);
        if (rootStart < 0 || content.charAt(rootStart) != '{') {
            return false;
        }

        int curlyDepth = 1;
        int squareDepth = 0;
        int entryStart = rootStart + 1;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;

        for (int index = rootStart + 1; index < content.length(); index++) {
            char current = content.charAt(index);

            if (doubleQuoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    doubleQuoted = false;
                }
                continue;
            }

            if (singleQuoted) {
                if (current == '\'' && index + 1 < content.length() && content.charAt(index + 1) == '\'') {
                    index++;
                } else if (current == '\'') {
                    singleQuoted = false;
                }
                continue;
            }

            if (current == '"') {
                doubleQuoted = true;
                continue;
            }
            if (current == '\'') {
                singleQuoted = true;
                continue;
            }
            if (current == '#' && isCommentStart(content, index)) {
                int newline = content.indexOf('\n', index + 1);
                if (newline < 0) {
                    return false;
                }
                index = newline;
                continue;
            }

            switch (current) {
                case '{' -> curlyDepth++;
                case '}' -> {
                    if (curlyDepth == 1 && squareDepth == 0) {
                        return isTargetFlowEntry(content.substring(entryStart, index), key);
                    }
                    curlyDepth--;
                }
                case '[' -> squareDepth++;
                case ']' -> squareDepth--;
                case ',' -> {
                    if (curlyDepth == 1 && squareDepth == 0) {
                        if (isTargetFlowEntry(content.substring(entryStart, index), key)) {
                            return true;
                        }
                        entryStart = index + 1;
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    private static boolean isTargetFlowEntry(String entry, String key) {
        String candidate = stripLeadingFlowComments(entry);
        if (candidate.isEmpty()) {
            return false;
        }

        int curlyDepth = 0;
        int squareDepth = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;

        for (int index = 0; index < candidate.length(); index++) {
            char current = candidate.charAt(index);

            if (doubleQuoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    doubleQuoted = false;
                }
                continue;
            }

            if (singleQuoted) {
                if (current == '\'' && index + 1 < candidate.length() && candidate.charAt(index + 1) == '\'') {
                    index++;
                } else if (current == '\'') {
                    singleQuoted = false;
                }
                continue;
            }

            if (current == '"') {
                doubleQuoted = true;
                continue;
            }
            if (current == '\'') {
                singleQuoted = true;
                continue;
            }

            switch (current) {
                case '{' -> curlyDepth++;
                case '}' -> curlyDepth--;
                case '[' -> squareDepth++;
                case ']' -> squareDepth--;
                case ':' -> {
                    if (curlyDepth == 0 && squareDepth == 0) {
                        return matchesKeyToken(candidate.substring(0, index), key);
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    private static boolean isExplicitKeyLine(String line, String key) {
        if (line.length() < 2 || line.charAt(0) != '?' || !Character.isWhitespace(line.charAt(1))) {
            return false;
        }
        return matchesKeyToken(stripTrailingComment(line), key);
    }

    private static boolean matchesKeyToken(String token, String key) {
        String trimmed = token.trim();
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '?' && Character.isWhitespace(trimmed.charAt(1))) {
            trimmed = trimmed.substring(1).stripLeading();
        }
        return trimmed.equals(key)
                || trimmed.equals("\"" + key + "\"")
                || trimmed.equals("'" + key + "'");
    }

    private static String stripTrailingComment(String value) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;

        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (doubleQuoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    doubleQuoted = false;
                }
                continue;
            }
            if (singleQuoted) {
                if (current == '\'' && index + 1 < value.length() && value.charAt(index + 1) == '\'') {
                    index++;
                } else if (current == '\'') {
                    singleQuoted = false;
                }
                continue;
            }
            if (current == '"') {
                doubleQuoted = true;
            } else if (current == '\'') {
                singleQuoted = true;
            } else if (current == '#' && index > 0 && Character.isWhitespace(value.charAt(index - 1))) {
                return value.substring(0, index).stripTrailing();
            }
        }
        return value;
    }

    private static String stripLeadingFlowComments(String entry) {
        String remaining = entry.stripLeading();
        while (remaining.startsWith("#")) {
            int newline = remaining.indexOf('\n');
            if (newline < 0) {
                return "";
            }
            remaining = remaining.substring(newline + 1).stripLeading();
        }
        return remaining;
    }

    private static int firstMeaningfulContentStart(String content) {
        int offset = 0;
        while (offset < content.length()) {
            int newline = content.indexOf('\n', offset);
            int lineEnd = newline < 0 ? content.length() : newline;
            String line = content.substring(offset, lineEnd);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }

            int indent = leadingIndent(line);
            String trimmed = line.substring(indent);
            if (!trimmed.isBlank()
                    && !trimmed.startsWith("#")
                    && !isDocumentMarker(trimmed)
                    && !trimmed.startsWith("%")) {
                return offset + indent;
            }

            if (newline < 0) {
                break;
            }
            offset = newline + 1;
        }
        return -1;
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

    private static boolean isCommentStart(String content, int index) {
        if (index == 0) {
            return true;
        }
        char previous = content.charAt(index - 1);
        return Character.isWhitespace(previous) || previous == ',' || previous == '{';
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
