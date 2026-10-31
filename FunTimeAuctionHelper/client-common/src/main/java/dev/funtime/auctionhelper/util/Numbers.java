package dev.funtime.auctionhelper.util;

import java.util.Locale;

/** Number parsing/formatting helpers shared by the price analyzer and the GUI. */
public final class Numbers {
    private static final String[] MULTIPLIERS = {"kk", "k", "m", "b"};
    private static final double[] MULTIPLIER_VALUES = {1_000_000d, 1_000d, 1_000_000d, 1_000_000_000d};

    private Numbers() {
    }

    /**
     * Parses a price that a server GUI or a user typed, e.g.
     * {@code "1 234,50"}, {@code "1.234.567"}, {@code "12.5k"}, {@code "3кк"}, {@code "1,500 $"}.
     *
     * @return the parsed value or {@code Double.NaN} when nothing sensible could be read
     */
    public static double parseAmount(String raw) {
        if (raw == null) {
            return Double.NaN;
        }
        StringBuilder digits = new StringBuilder();
        double multiplier = 1d;
        String text = raw.toLowerCase(Locale.ROOT).replace('\u00A0', ' ');
        boolean dropRest = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                if (!dropRest) {
                    digits.append(c);
                }
                continue;
            }
            if (c == ' ' || c == '\'' || c == '_') {
                continue;
            }
            if (c == '.' || c == ',') {
                // Look ahead: a separator followed by exactly three digits and then a
                // non digit is a thousands separator, otherwise it is a decimal comma.
                int nextDigits = 0;
                int j = i + 1;
                while (j < text.length() && Character.isDigit(text.charAt(j))) {
                    nextDigits++;
                    j++;
                }
                boolean thousands = nextDigits == 3 && (j >= text.length() || !Character.isDigit(text.charAt(j)));
                if (thousands) {
                    continue;
                }
                if (digits.indexOf(".") < 0 && !dropRest) {
                    digits.append('.');
                    // Only two decimal digits matter for prices.
                    int appended = 0;
                    for (int k = i + 1; k < text.length() && appended < 2; k++) {
                        char d = text.charAt(k);
                        if (Character.isDigit(d)) {
                            digits.append(d);
                            appended++;
                        } else {
                            break;
                        }
                    }
                    dropRest = true;
                }
                continue;
            }
            if (isSuffixLetter(c)) {
                // "2к" = 2000, "2кк" = 2 000 000, "2ккк" = 2 000 000 000, "2м" = 2 000 000,
                // "2b" = 2 000 000 000. The run length matters, so consume the whole run here.
                int run = 0;
                while (i + run < text.length() && text.charAt(i + run) == c) {
                    run++;
                }
                i += run - 1;
                multiplier = multiplierForSuffix(c, run);
                dropRest = true;
                continue;
            }
        }
        if (digits.length() == 0) {
            return Double.NaN;
        }
        String normalized = digits.toString();
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            return Double.parseDouble(normalized) * multiplier;
        } catch (NumberFormatException error) {
            return Double.NaN;
        }
    }

    private static boolean isSuffixLetter(char c) {
        return c == 'k' || c == 'm' || c == 'b' || c == '\u043A' || c == '\u043C' || c == '\u0442'
                || c == '\u041A' || c == '\u041C' || c == '\u0422';
    }

    /** Russian/English shorthand: k = thousand, m = million, b = billion; repeats multiply. */
    private static double multiplierForSuffix(char c, int run) {
        int steps = run < 1 ? 1 : run;
        if (c == 'm' || c == '\u043C' || c == '\u041C') {
            return Math.pow(1000d, 2 + (steps - 1));
        }
        if (c == 'b') {
            return Math.pow(1000d, 3 + (steps - 1));
        }
        // k, к, т -> 1000^run
        return Math.pow(1000d, steps);
    }

    private static double multiplierFor(String suffix) {
        for (int i = 0; i < MULTIPLIERS.length; i++) {
            if (MULTIPLIERS[i].equals(suffix)) {
                return MULTIPLIER_VALUES[i];
            }
        }
        return 1d;
    }

    /** Formats {@code 1234.5} as {@code "1,234"} and {@code 12.75} as {@code "12.75"}. */
    public static String formatAmount(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "-";
        }
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.005d) {
            return group(Math.abs(rounded)) .isEmpty() ? "0" : (value < 0 ? "-" : "") + group(Math.abs(rounded));
        }
        String base = group(Math.abs(rounded));
        int decimals = (int) Math.round(Math.abs(value - rounded) * 100d);
        if (decimals == 0) {
            return (value < 0 ? "-" : "") + base;
        }
        String dec = String.valueOf(decimals);
        while (dec.length() < 2) {
            dec = "0" + dec;
        }
        return (value < 0 ? "-" : "") + base + "." + dec;
    }

    private static String group(long value) {
        String raw = Long.toString(value);
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (int i = raw.length() - 1; i >= 0; i--) {
            out.append(raw.charAt(i));
            count++;
            if (count % 3 == 0 && i > 0) {
                out.append(',');
            }
        }
        return out.reverse().toString();
    }

    public static String formatPercent(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100d);
    }

    public static String formatSigned(double value) {
        String formatted = formatAmount(Math.abs(value));
        return value >= 0 ? "+" + formatted : "-" + formatted;
    }

    public static int clampInt(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static boolean isPositiveFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0d;
    }
}
