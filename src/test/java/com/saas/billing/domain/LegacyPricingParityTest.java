package com.saas.billing.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Prova de equivalência: compara o PricingCalculator com uma cópia literal da
 * fórmula original (double, linhas 24-57 do legado) numa grade de entradas.
 * A única diferença admitida é o arredondamento para centavos do Money.
 */
class LegacyPricingParityTest {

    private static final double TOLERANCE = 0.03;

    /** Cópia fiel do cálculo legado, só trocando LocalDate.now() por parâmetro. */
    private static double legacyPrice(double basePrice, LocalDate signupDate, int overdueDays,
                                      String promoCode, boolean isNonProfit, LocalDate today) {
        int monthsActive = (today.getYear() - signupDate.getYear()) * 12 + (today.getMonthValue() - signupDate.getMonthValue());

        double calculatedPrice = basePrice;

        if (monthsActive > 24) {
            calculatedPrice = calculatedPrice - (basePrice * 0.15);
        } else if (monthsActive > 12) {
            calculatedPrice = calculatedPrice - (basePrice * 0.10);
        }

        if (isNonProfit) {
            calculatedPrice = calculatedPrice * 0.70;
        }

        if (promoCode != null && !promoCode.trim().isEmpty()) {
            if (promoCode.equalsIgnoreCase("BLACKFRIDAY") && today.getMonthValue() == 11) {
                calculatedPrice = calculatedPrice - 30.0;
            } else if (promoCode.equalsIgnoreCase("WELCOME10") && monthsActive <= 1) {
                calculatedPrice = calculatedPrice * 0.90;
            }
        }

        if (overdueDays > 0) {
            double penalty = calculatedPrice * 0.02;
            double dailyInterest = (calculatedPrice * 0.001) * overdueDays;
            calculatedPrice = calculatedPrice + penalty + dailyInterest;
        }

        if (calculatedPrice < 0) {
            calculatedPrice = 0.0;
        }
        return calculatedPrice;
    }

    @Test
    void matchesLegacyFormulaWithinOneRoundingStepAcrossTheInputGrid() {
        PricingCalculator calculator = new PricingCalculator();
        String[] bases = {"0.00", "9.99", "19.90", "20.00", "100.00", "249.99", "1999.00"};
        int[] months = {0, 1, 2, 11, 12, 13, 23, 24, 25, 60};
        String[] promos = {null, "", "  ", "BLACKFRIDAY", "blackfriday", "WELCOME10", "welcome10", "OTHER"};
        int[] overdue = {0, 1, 10, 45};
        LocalDate[] todays = {LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 27)};

        int cases = 0;
        for (String base : bases) {
            for (int m : months) {
                for (String promo : promos) {
                    for (int days : overdue) {
                        for (boolean ngo : new boolean[] {false, true}) {
                            for (LocalDate today : todays) {
                                LocalDate signup = today.withDayOfMonth(1).minusMonths(m);
                                double legacy = legacyPrice(Double.parseDouble(base), signup, days, promo, ngo, today);
                                Money actual = calculator.calculate(
                                        new BillingCommand("c", "PRO", Money.of(base), signup, days, promo, ngo, "tok_1"), today);

                                double diff = Math.abs(actual.amount().subtract(BigDecimal.valueOf(legacy)).doubleValue());
                                assertTrue(diff <= TOLERANCE, () -> "divergiu " + diff + " base=" + base + " m=" + m
                                        + " promo=" + promo + " dias=" + days + " ong=" + ngo + " hoje=" + today
                                        + " legado=" + legacy + " novo=" + actual);
                                cases++;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(cases > 8000, "grade menor que o esperado: " + cases);
    }
}
