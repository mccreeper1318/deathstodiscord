package com.pinnacle.deathstodiscord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

        FlowState flowState = new FlowState();
        for (String line : content.split("\\R", -1)) {
            int indent = leadingIndent(line);
            boolean insideFlow = flowState.insideFlow();
            String trimmed = line.substring(indent);

            if (!insideFlow
                    && indent == rootIndent
                    && !isIgnorableRootLine(trimmed)
                    && (isSimpleMappingKeyLine(trimmed, key) || isExplicitKeyLine(trimmed, key))) {
                return true;
            }

            updateFlowState(line, flowState);
        }
        return false;
    }

    private static boolean containsRootFlowMappingKey(String content, String key) {
        int rootStart = firstMeaningfulContentStart(content);
        if (rootStart < 0) {
            return false;
        }

        int flowStart = skipRootNodeDecorators(content, rootStart);
        if (flowStart < 0 || flowStart >= content.length() || content.charAt(flowStart) != '{') {
            return false;
        }

        int curlyDepth = 1;
        int squareDepth = 0;
        int entryStart = flowStart + 1;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;

        for (int index = flowStart + 1; index < content.length(); index++) {
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

        String mapping = stripLeadingKeyDecorators(candidate);
        int colonIndex = findMappingColon(mapping);
        return colonIndex >= 0 && matchesKeyToken(mapping.substring(0, colonIndex), key);
    }

    private static boolean isSimpleMappingKeyLine(String line, String key) {
        String candidate = stripTrailingComment(line);
        String mapping = stripLeadingKeyDecorators(candidate);
        int colonIndex = findMappingColon(mapping);
        return colonIndex >= 0 && matchesKeyToken(mapping.substring(0, colonIndex), key);
    }

    private static int findMappingColon(String candidate) {
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
                        return index;
                    }
                }
                default -> {
                }
            }
        }
        return -1;
    }

    private static boolean isExplicitKeyLine(String line, String key) {
        if (line.length() < 2 || line.charAt(0) != '?' || !Character.isWhitespace(line.charAt(1))) {
            return false;
        }
        return matchesKeyToken(stripTrailingComment(line), key);
    }

    private static boolean matchesKeyToken(String token, String key) {
        String normalized = stripLeadingKeyDecorators(token);
        String decoded = decodeYamlKeyScalar(normalized);
        return key.equals(decoded);
    }

    private static String stripLeadingKeyDecorators(String token) {
        String remaining = token.trim();
        if (remaining.length() >= 2 && remaining.charAt(0) == '?' && Character.isWhitespace(remaining.charAt(1))) {
            remaining = remaining.substring(1).stripLeading();
        }

        while (!remaining.isEmpty()) {
            int propertyLength = yamlNodePropertyLength(remaining, 0);
            if (propertyLength < 0) {
                break;
            }
            if (propertyLength >= remaining.length()) {
                return "";
            }
            if (!Character.isWhitespace(remaining.charAt(propertyLength))) {
                break;
            }
            remaining = remaining.substring(propertyLength).stripLeading();
        }
        return remaining;
    }

    private static int skipRootNodeDecorators(String content, int start) {
        int index = start;
        while (index < content.length()) {
            int propertyLength = yamlNodePropertyLength(content, index);
            if (propertyLength < 0) {
                return index;
            }

            int afterProperty = index + propertyLength;
            int afterSeparation = skipYamlSeparation(content, afterProperty);
            if (afterSeparation == afterProperty) {
                return index;
            }
            index = afterSeparation;
        }
        return index;
    }

    private static int skipYamlSeparation(String content, int start) {
        int index = start;
        boolean consumed = false;

        while (index < content.length()) {
            while (index < content.length() && Character.isWhitespace(content.charAt(index))) {
                index++;
                consumed = true;
            }

            if (index < content.length() && content.charAt(index) == '#') {
                int newline = content.indexOf('\n', index + 1);
                if (newline < 0) {
                    return content.length();
                }
                index = newline + 1;
                consumed = true;
                continue;
            }
            break;
        }

        return consumed ? index : start;
    }

    private static int yamlNodePropertyLength(String value, int start) {
        if (start < 0 || start >= value.length()) {
            return -1;
        }

        char first = value.charAt(start);
        if (first == '&') {
            int end = start + 1;
            while (end < value.length() && !isPropertyTerminator(value.charAt(end))) {
                end++;
            }
            return end > start + 1 ? end - start : -1;
        }
        if (first != '!') {
            return -1;
        }

        if (start + 1 < value.length() && value.charAt(start + 1) == '<') {
            int close = value.indexOf('>', start + 2);
            return close < 0 ? -1 : close + 1 - start;
        }

        int end = start + 1;
        while (end < value.length() && !isPropertyTerminator(value.charAt(end))) {
            end++;
        }
        return end - start;
    }

    private static boolean isPropertyTerminator(char value) {
        return Character.isWhitespace(value)
                || value == '['
                || value == ']'
                || value == '{'
                || value == '}'
                || value == ',';
    }

    private static String decodeYamlKeyScalar(String token) {
        if (token.length() >= 2 && token.charAt(0) == '\'' && token.charAt(token.length() - 1) == '\'') {
            return token.substring(1, token.length() - 1).replace("''", "'");
        }
        if (token.length() >= 2 && token.charAt(0) == '"' && token.charAt(token.length() - 1) == '"') {
            return decodeDoubleQuotedScalar(token.substring(1, token.length() - 1));
        }
        return token;
    }

    private static String decodeDoubleQuotedScalar(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\') {
                decoded.append(current);
                continue;
            }

            if (++index >= value.length()) {
                return null;
            }

            char escape = value.charAt(index);
            switch (escape) {
                case '0' -> decoded.append('\0');
                case 'a' -> decoded.append('\u0007');
                case 'b' -> decoded.append('\b');
                case 't', '\t' -> decoded.append('\t');
                case 'n' -> decoded.append('\n');
                case 'v' -> decoded.append('\u000B');
                case 'f' -> decoded.append('\f');
                case 'r' -> decoded.append('\r');
                case 'e' -> decoded.append('\u001B');
                case ' ' -> decoded.append(' ');
                case '"' -> decoded.append('"');
                case '/' -> decoded.append('/');
                case '\\' -> decoded.append('\\');
                case 'N' -> decoded.append('\u0085');
                case '_' -> decoded.append('\u00A0');
                case 'L' -> decoded.append('\u2028');
                case 'P' -> decoded.append('\u2029');
                case 'x' -> {
                    Integer codePoint = parseHexEscape(value, index + 1, 2);
                    if (codePoint == null) {
                        return null;
                    }
                    decoded.append((char) codePoint.intValue());
                    index += 2;
                }
                case 'u' -> {
                    Integer codePoint = parseHexEscape(value, index + 1, 4);
                    if (codePoint == null) {
                        return null;
                    }
                    decoded.append((char) codePoint.intValue());
                    index += 4;
                }
                case 'U' -> {
                    Integer codePoint = parseHexEscape(value, index + 1, 8);
                    if (codePoint == null || !Character.isValidCodePoint(codePoint)) {
                        return null;
                    }
                    decoded.appendCodePoint(codePoint);
                    index += 8;
                }
                default -> {
                    return null;
                }
            }
        }
        return decoded.toString();
    }

    private static Integer parseHexEscape(String value, int start, int length) {
        if (start + length > value.length()) {
            return null;
        }
        int result = 0;
        for (int index = start; index < start + length; index++) {
            int digit = Character.digit(value.charAt(index), 16);
            if (digit < 0) {
                return null;
            }
            result = (result << 4) | digit;
        }
        return result;
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
            if (!isIgnorableRootLine(trimmed)) {
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
        FlowState flowState = new FlowState();

        for (String line : content.split("\\R", -1)) {
            int indent = leadingIndent(line);
            boolean insideFlow = flowState.insideFlow();
            String trimmed = line.substring(indent);

            if (!insideFlow && !isIgnorableRootLine(trimmed)) {
                rootIndent = Math.min(rootIndent, indent);
            }

            updateFlowState(line, flowState);
        }
        return rootIndent == Integer.MAX_VALUE ? -1 : rootIndent;
    }

    private static boolean isIgnorableRootLine(String trimmed) {
        return trimmed.isBlank()
                || trimmed.startsWith("#")
                || isDocumentMarker(trimmed)
                || trimmed.startsWith("%");
    }

    private static void updateFlowState(String line, FlowState state) {
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);

            if (state.doubleQuoted) {
                if (state.escaped) {
                    state.escaped = false;
                } else if (current == '\\') {
                    state.escaped = true;
                } else if (current == '"') {
                    state.doubleQuoted = false;
                }
                continue;
            }

            if (state.singleQuoted) {
                if (current == '\'' && index + 1 < line.length() && line.charAt(index + 1) == '\'') {
                    index++;
                } else if (current == '\'') {
                    state.singleQuoted = false;
                }
                continue;
            }

            if (current == '"') {
                state.doubleQuoted = true;
                continue;
            }
            if (current == '\'') {
                state.singleQuoted = true;
                continue;
            }
            if (current == '#' && isCommentStart(line, index)) {
                break;
            }

            switch (current) {
                case '{' -> state.curlyDepth++;
                case '}' -> state.curlyDepth = Math.max(0, state.curlyDepth - 1);
                case '[' -> state.squareDepth++;
                case ']' -> state.squareDepth = Math.max(0, state.squareDepth - 1);
                default -> {
                }
            }
        }

        if (!state.doubleQuoted) {
            state.escaped = false;
        }
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

    private static final class FlowState {
        private int curlyDepth;
        private int squareDepth;
        private boolean singleQuoted;
        private boolean doubleQuoted;
        private boolean escaped;

        private boolean insideFlow() {
            return curlyDepth > 0 || squareDepth > 0;
        }
    }
}
