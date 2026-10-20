package com.saas.billing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Testa o cálculo direto, sem orquestrador, portas ou fakes. */
class PricingCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private final PricingCalculator calculator = new PricingCalculator();

    private Money price(String base, int monthsActive, int overdueDays, String promo, boolean nonProfit, LocalDate today) {
        LocalDate signup = today.withDayOfMonth(1).minusMonths(monthsActive);
        return calculator.calculate(new BillingCommand("c", "PRO", Money.of(base), signup, overdueDays, promo, nonProfit, null), today);
    }

    @Test
    void loyaltyTiersStartStrictlyAfter12And24Months() {
        assertEquals(Money.of("100.00"), price("100.00", 12, 0, null, false, TODAY));
        assertEquals(Money.of("90.00"), price("100.00", 13, 0, null, false, TODAY));
        assertEquals(Money.of("90.00"), price("100.00", 24, 0, null, false, TODAY));
        assertEquals(Money.of("85.00"), price("100.00", 25, 0, null, false, TODAY));
    }

    @Test
    void discountsStackInOrderLoyaltyThenNonProfitThenPromo() {
        // 100 -> 90 (fidelidade) -> 63 (ONG) ; WELCOME10 não vale após 1 mês
        assertEquals(Money.of("63.00"), price("100.00", 13, 0, "WELCOME10", true, TODAY));
        // 100 -> 70 (ONG) -> 40 (BLACKFRIDAY em novembro)
        assertEquals(Money.of("40.00"), price("100.00", 0, 0, "BLACKFRIDAY", true, LocalDate.of(2026, 11, 3)));
    }

    @Test
    void lateFeesApplyPenaltyPlusDailyInterestOverTheDiscountedPrice() {
        // 100,00 * 0,02 = 2,00 ; 100,00 * 0,001 * 10 = 1,00
        assertEquals(Money.of("103.00"), price("100.00", 0, 10, null, false, TODAY));
        assertEquals(Money.of("100.00"), price("100.00", 0, 0, null, false, TODAY));
        assertEquals(Money.of("100.00"), price("100.00", 0, -5, null, false, TODAY));
    }

    @Test
    void priceNeverGoesNegative() {
        assertEquals(Money.ZERO, price("20.00", 0, 0, "BLACKFRIDAY", false, LocalDate.of(2026, 11, 3)));
    }

    @Test
    void sameInputsAndDateAlwaysGiveSameResult() {
        assertEquals(price("249.99", 30, 7, "WELCOME10", true, TODAY), price("249.99", 30, 7, "WELCOME10", true, TODAY));
    }
}
