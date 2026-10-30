package dev.survivortoolkit;

import dev.survivortoolkit.auction.PriceCalculator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PriceCalculatorTest {
    @Test void calculatesManualQuote() {
        var quote = PriceCalculator.calculate("120", "16", "8");
        assertEquals("7.5", PriceCalculator.format(quote.unit()));
        assertEquals("480", PriceCalculator.format(quote.stack()));
        assertTrue(quote.atOrBelowReference());
    }
    @Test void comparesWithoutRoundingOrZeroReference() {
        assertFalse(PriceCalculator.calculate("1", "3", "0.3333").atOrBelowReference());
        assertFalse(PriceCalculator.calculate("0", "1", "0").atOrBelowReference());
        assertTrue(PriceCalculator.calculate("1,5", "3", "0,5").atOrBelowReference());
    }
    @Test void rejectsBadPricesAndQuantities() {
        for (String s : new String[]{"NaN", "-1", "1e5", "", "1000000000001", "1.1234567"})
            assertThrows(IllegalArgumentException.class, () -> PriceCalculator.price(s));
        for (String s : new String[]{"0", "-1", "2305", "garbage"})
            assertThrows(IllegalArgumentException.class, () -> PriceCalculator.calculate("1", s, "1"));
    }
}
