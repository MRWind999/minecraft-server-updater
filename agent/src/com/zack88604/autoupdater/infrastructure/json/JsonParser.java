package com.zack88604.autoupdater.infrastructure.json;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal JSON field extraction for the updater protocol, without external dependencies.
 *
 * <p>A key is matched only where it is an object key (followed by {@code :}),
 * never where the same text happens to appear inside a string value. Without
 * that rule, a manifest that manages a path literally named {@code agent} made
 * {@link #getObject(String, String)} return the first file entry as the
 * self-update section. Container scanning also skips quoted strings, so a path
 * containing braces or brackets cannot shift the parsed structure.</p>
 */
public final class JsonParser {

    private JsonParser() {
    }

    public static String getString(String json, String key) {
        int keyIndex = keyIndex(json, key);
        if (keyIndex < 0) {
            return null;
        }
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find(keyIndex) ? matcher.group(1) : null;
    }

    public static int getInt(String json, String key, int defaultValue) {
        int keyIndex = keyIndex(json, key);
        if (keyIndex < 0) {
            return defaultValue;
        }
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find(keyIndex)) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // Fall through to the caller-provided default.
            }
        }
        return defaultValue;
    }

    public static long getLong(String json, String key, long defaultValue) {
        int keyIndex = keyIndex(json, key);
        if (keyIndex < 0) {
            return defaultValue;
        }
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find(keyIndex)) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // Fall through to the caller-provided default.
            }
        }
        return defaultValue;
    }

    public static String getObject(String json, String key) {
        int keyIndex = keyIndex(json, key);
        if (keyIndex < 0) {
            return null;
        }
        int start = json.indexOf('{', keyIndex);
        return start < 0 ? null : extractContainer(json, start, '{', '}');
    }

    public static String getArray(String json, String key) {
        int keyIndex = keyIndex(json, key);
        if (keyIndex < 0) {
            return null;
        }
        int start = json.indexOf('[', keyIndex);
        if (start < 0) {
            return null;
        }
        String container = extractContainer(json, start, '[', ']');
        return container == null ? null : container.substring(1, container.length() - 1).trim();
    }

    public static List<String> parseStringArray(String array) {
        List<String> values = new ArrayList<>();
        if (array.isEmpty()) {
            return values;
        }
        Pattern pattern = Pattern.compile("\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(array);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        if (values.isEmpty() && !array.isEmpty()) {
            values.add("*");
        }
        return values;
    }

    /**
     * Locate {@code "key"} only where it is followed by a colon, so a string
     * value that happens to equal the key name is never treated as the key.
     */
    private static int keyIndex(String json, String key) {
        if (json == null || key == null) {
            return -1;
        }
        String token = "\"" + key + "\"";
        int from = 0;
        while (from <= json.length() - token.length()) {
            int index = json.indexOf(token, from);
            if (index < 0) {
                return -1;
            }
            int next = index + token.length();
            while (next < json.length() && Character.isWhitespace(json.charAt(next))) {
                next++;
            }
            if (next < json.length() && json.charAt(next) == ':') {
                return index;
            }
            from = index + 1;
        }
        return -1;
    }

    /** Return the balanced container starting at {@code start}, ignoring quoted text. */
    private static String extractContainer(String json, int start, char open, char close) {
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = start; index < json.length(); index++) {
            char value = json.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == '"') {
                    quoted = false;
                }
            } else if (value == '"') {
                quoted = true;
            } else if (value == open) {
                depth++;
            } else if (value == close && --depth == 0) {
                return json.substring(start, index + 1);
            }
        }
        return null;
    }
}
