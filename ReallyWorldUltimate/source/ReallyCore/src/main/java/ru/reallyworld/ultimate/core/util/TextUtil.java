package ru.reallyworld.ultimate.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;

public final class TextUtil {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final ThreadLocal<DecimalFormat> NUMBER_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("#,###", sym);
        df.setGroupingUsed(true);
        return df;
    });

    private TextUtil() {}

    public static Component mm(String template) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }
        return MM.deserialize(template).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component mm(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }
        String resolved = replacePlaceholders(template, placeholders);
        return mm(resolved);
    }

    public static String replacePlaceholders(String template, Map<String, String> placeholders) {
        if (template == null || placeholders == null || placeholders.isEmpty()) {
            return template == null ? "" : template;
        }
        String out = template;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String val = e.getValue() == null ? "" : e.getValue();
            out = out.replace("<" + e.getKey() + ">", val);
        }
        return out;
    }

    public static String plain(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }

    public static String formatAmount(long value) {
        return NUMBER_FORMAT.get().format(value);
    }

    public static String formatDuration(long totalSeconds) {
        if (totalSeconds <= 0) return "0с";
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("д ");
        if (hours > 0) sb.append(hours).append("ч ");
        if (minutes > 0) sb.append(minutes).append("м ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("с");
        return sb.toString().trim();
    }

    public static String sanitizeInput(String raw, int maxLen) {
        if (raw == null) return "";
        String cleaned = raw
                .replaceAll("<[^>]*>", "")
                .replaceAll("§[0-9A-FK-ORa-fk-orx]", "")
                .replace("<", "").replace(">", "").replace("§", "")
                .trim();
        return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) : cleaned;
    }
}
