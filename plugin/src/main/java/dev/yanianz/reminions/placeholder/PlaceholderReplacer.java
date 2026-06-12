package dev.yanianz.reminions.placeholder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class PlaceholderReplacer {

    public static String replaceMapText(String text, Map<String, Object> replacements) {
        if (text == null || replacements == null || replacements.isEmpty()) return text;
        for (Entry<String, Object> entry : replacements.entrySet()) {
            text = text.replace(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return text;
    }

    public static List<String> replaceMapLines(List<String> lines, Map<String, Object> replacements) {
        if (lines == null || replacements == null || replacements.isEmpty()) return lines;
        return lines.stream().map(line -> replaceMapText(line, replacements)).toList();
    }

    public static String replaceInlineText(String text, Object... pairs) {
        if (text == null || pairs == null || pairs.length % 2 != 0) return text;
        Map<String, Object> map = toMap(pairs);
        for (Entry<String, Object> entry : map.entrySet()) {
            text = text.replace(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    public static List<String> replaceInlineLines(List<String> lines, Object... pairs) {
        if (lines == null || pairs == null || pairs.length % 2 != 0) return List.of();
        Map<String, Object> map = toMap(pairs);
        List<String> result = new ArrayList<>(lines.size());

        for (String line : lines) {
            boolean expanded = false;
            for (Entry<String, Object> entry : map.entrySet()) {
                if (entry.getValue() instanceof List<?> listVal && line.contains(entry.getKey())) {
                    for (Object elem : (List<Object>) listVal) {
                        if (elem instanceof String s) result.add(line.replace(entry.getKey(), s));
                    }
                    expanded = true;
                    break;
                }
            }
            if (!expanded) result.add(replaceInlineText(line, pairs));
        }
        return result;
    }

    private static Map<String, Object> toMap(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Placeholders must be in key-value pairs.");
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
