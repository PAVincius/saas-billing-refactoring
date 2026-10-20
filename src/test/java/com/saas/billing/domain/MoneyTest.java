package com.saas.billing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesToTwoDecimalsSoEqualValuesAreEqual() {
        assertEquals(Money.of("10"), Money.of("10.00"));
        assertEquals(Money.of("10.0"), Money.of("10.000"));
    }

    @Test
    void roundsHalfUpToCents() {
        assertEquals(Money.of("0.13"), Money.of("0.125"));
        assertEquals(Money.of("0.12"), Money.of("0.124"));
        assertEquals(Money.of("-0.13"), Money.of("-0.125"));
    }

    @Test
    void plusAndMinusReturnNewInstancesWithoutMutatingOperands() {
        Money a = Money.of("10.50");
        Money b = Money.of("0.25");

        assertEquals(Money.of("10.75"), a.plus(b));
        assertEquals(Money.of("10.25"), a.minus(b));
        assertEquals(Money.of("10.50"), a);
        assertEquals(Money.of("0.25"), b);
    }

    @Test
    void timesRoundsResultToCents() {
        assertEquals(Money.of("70.00"), Money.of("100.00").times(new BigDecimal("0.70")));
        assertEquals(Money.of("3.33"), Money.of("10.00").times(new BigDecimal("0.3333")));
    }

    @Test
    void orZeroIfNegativeClampsOnlyNegatives() {
        assertEquals(Money.ZERO, Money.of("-5.00").orZeroIfNegative());
        assertEquals(Money.of("5.00"), Money.of("5.00").orZeroIfNegative());
        assertEquals(Money.ZERO, Money.ZERO.orZeroIfNegative());
    }

    @Test
    void isNegativeDetectsSign() {
        assertTrue(Money.of("-0.01").isNegative());
        assertFalse(Money.ZERO.isNegative());
        assertFalse(Money.of("0.01").isNegative());
    }

    @Test
    void toStringIsPlainWithTwoDecimals() {
        assertEquals("90.00", Money.of("90").toString());
        assertEquals("0.00", Money.ZERO.toString());
        assertEquals("1000000.50", Money.of("1000000.5").toString());
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(NullPointerException.class, () -> new Money(null));
    }
}
