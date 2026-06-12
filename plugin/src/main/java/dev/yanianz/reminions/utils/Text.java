package dev.yanianz.reminions.utils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import dev.yanianz.reminions.placeholder.PlaceholderReplacer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Text {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###.0");
    private static final int[]    ROMAN_VALUES   = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_NUMERALS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
    private static final Pattern  COLOR_PATTERN  =
            Pattern.compile("(&[0-9a-fk-orK-OR])|(&x(&[0-9a-fA-F]){6})|(#(?:[0-9a-fA-F]{3}){1,2})");

    static {
        DECIMAL_FORMAT.setGroupingUsed(true);
    }

    public static Component parseComponent(String text, Object... placeholders) {
        if (text == null) return Component.empty();
        text = PlaceholderReplacer.replaceInlineText(text, placeholders);
        Matcher matcher = COLOR_PATTERN.matcher(text);
        Builder builder = Component.empty().toBuilder().decoration(TextDecoration.ITALIC, false);
        int cursor = 0;
        TextColor currentColor = null;
        EnumSet<TextDecoration> decorations = EnumSet.noneOf(TextDecoration.class);

        while (matcher.find()) {
            String segment = text.substring(cursor, matcher.start());
            if (!segment.isEmpty()) builder.append(styledText(segment, currentColor, decorations));

            String token = matcher.group();
            if (token.startsWith("&x")) {
                currentColor = parseHexColorCode(token);
            } else if (token.startsWith("#")) {
                currentColor = TextColor.fromHexString(token);
            } else if (token.startsWith("&")) {
                applyDecorations(token, decorations);
                NamedTextColor named = getNamedTextColor(token.charAt(1));
                if (named != null) currentColor = named;
            }
            cursor = matcher.end();
        }

        if (cursor < text.length()) {
            builder.append(styledText(text.substring(cursor), currentColor, decorations));
        }
        return builder.build();
    }

    public static List<Component> parseComponents(List<String> lines, Object... placeholders) {
        if (lines == null) return List.of();
        List<Component> result = new ArrayList<>(lines.size());
        for (String line : PlaceholderReplacer.replaceInlineLines(lines, placeholders)) {
            result.add(parseComponent(line));
        }
        return result;
    }

    public static String toLegacyString(Component component) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(component);
    }

    private static Component styledText(String text, TextColor color, Set<TextDecoration> decorations) {
        return Component.text(text).style(style -> {
            style.color(color);
            decorations.forEach(d -> style.decoration(d, true));
        });
    }

    private static void applyDecorations(String token, Set<TextDecoration> decorations) {
        for (char c : token.substring(1).toCharArray()) {
            switch (Character.toLowerCase(c)) {
                case 'l' -> decorations.add(TextDecoration.BOLD);
                case 'm' -> decorations.add(TextDecoration.STRIKETHROUGH);
                case 'n' -> decorations.add(TextDecoration.UNDERLINED);
                case 'o' -> decorations.add(TextDecoration.ITALIC);
                case 'r' -> decorations.clear();
            }
        }
    }

    public static NamedTextColor getNamedTextColor(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a' -> NamedTextColor.GREEN;
            case 'b' -> NamedTextColor.AQUA;
            case 'c' -> NamedTextColor.RED;
            case 'd' -> NamedTextColor.LIGHT_PURPLE;
            case 'e' -> NamedTextColor.YELLOW;
            case 'f' -> NamedTextColor.WHITE;
            default  -> null;
        };
    }

    private static TextColor parseHexColorCode(String token) {
        StringBuilder hex = new StringBuilder("#");
        for (int i = 3; i < token.length(); i += 2) {
            hex.append(token.charAt(i));
        }
        return TextColor.fromHexString(hex.toString());
    }

    public static String toRomanLevel(int value) {
        if (value <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (value >= ROMAN_VALUES[i]) {
                value -= ROMAN_VALUES[i];
                sb.append(ROMAN_NUMERALS[i]);
            }
        }
        return sb.toString();
    }

    public static String format1(double value) {
        return value % 1.0 == 0.0
                ? new DecimalFormat("#,###").format((long) value)
                : DECIMAL_FORMAT.format(Math.floor(value * 10.0) / 10.0);
    }

    public static String getFormattedDurationLeft(long appliedAt, long duration) {
        if (duration < 0L) return "∞";
        long now = System.currentTimeMillis();
        long remaining = appliedAt + duration - now;
        if (remaining <= 0L) return "0s";

        long totalSeconds = remaining / 1000L;
        long days    = totalSeconds / 86400L; totalSeconds %= 86400L;
        long hours   = totalSeconds / 3600L;  totalSeconds %= 3600L;
        long minutes = totalSeconds / 60L;    totalSeconds %= 60L;

        StringBuilder sb = new StringBuilder();
        if (days    > 0) sb.append(days).append("d ");
        if (hours   > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (totalSeconds > 0 || sb.isEmpty()) sb.append(totalSeconds).append("s");
        return sb.toString().trim();
    }
}
