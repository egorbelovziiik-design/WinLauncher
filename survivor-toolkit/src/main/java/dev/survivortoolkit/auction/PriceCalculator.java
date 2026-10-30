package dev.survivortoolkit.auction;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure, deliberately manual arithmetic; it never reads a server screen or performs clicks. */
public final class PriceCalculator {
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000000000");
    private PriceCalculator() {}

    public record Quote(BigDecimal total, int count, BigDecimal unit, BigDecimal stack, boolean atOrBelowReference) {}

    public static BigDecimal price(String value) {
        if (value == null || value.length() > 32) throw new IllegalArgumentException("invalid price");
        String normalized = value.trim().replace(',', '.');
        if (!normalized.matches("[0-9]{1,13}(\\.[0-9]{1,6})?")) throw new IllegalArgumentException("invalid price");
        BigDecimal parsed = new BigDecimal(normalized);
        if (parsed.signum() < 0 || parsed.compareTo(MAX_PRICE) > 0) throw new IllegalArgumentException("price out of range");
        return parsed;
    }

    public static Quote calculate(String totalText, String countText, String referenceText) {
        BigDecimal total = price(totalText);
        BigDecimal reference = price(referenceText);
        int count;
        try { count = Integer.parseInt(countText.trim()); } catch (RuntimeException e) { throw new IllegalArgumentException("invalid count", e); }
        if (count < 1 || count > 2304) throw new IllegalArgumentException("count out of range");
        BigDecimal unit = total.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
        BigDecimal stack = unit.multiply(BigDecimal.valueOf(64)).setScale(4, RoundingMode.HALF_UP);
        // Compare without rounding so that a displayed approximation cannot produce a false bargain.
        boolean match = reference.signum() > 0 && total.compareTo(reference.multiply(BigDecimal.valueOf(count))) <= 0;
        return new Quote(total, count, unit, stack, match);
    }

    public static String format(BigDecimal number) {
        return number.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
