package com.saas.billing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Regras de precificação mensal. Classe de domínio pura: sem I/O, sem relógio
 * próprio (a data chega por parâmetro) e sem estado.
 */
public class PricingCalculator {

    private static final int LOYALTY_TIER_2_MONTHS = 24;
    private static final int LOYALTY_TIER_1_MONTHS = 12;
    private static final BigDecimal LOYALTY_TIER_2_DISCOUNT = new BigDecimal("0.15");
    private static final BigDecimal LOYALTY_TIER_1_DISCOUNT = new BigDecimal("0.10");
    private static final BigDecimal NON_PROFIT_FACTOR = new BigDecimal("0.70");
    private static final String BLACK_FRIDAY_CODE = "BLACKFRIDAY";
    private static final int BLACK_FRIDAY_MONTH = 11;
    private static final Money BLACK_FRIDAY_DISCOUNT = Money.of("30.00");
    private static final String WELCOME_CODE = "WELCOME10";
    private static final int WELCOME_MAX_MONTHS = 1;
    private static final BigDecimal WELCOME_FACTOR = new BigDecimal("0.90");
    private static final BigDecimal LATE_PENALTY_RATE = new BigDecimal("0.02");
    private static final BigDecimal LATE_DAILY_INTEREST_RATE = new BigDecimal("0.001");

    public Money calculate(BillingCommand command, LocalDate today) {
        int monthsActive = monthsActive(command.signupDate(), today);

        Money price = applyLoyaltyDiscount(command.basePrice(), monthsActive);
        price = applyNonProfitDiscount(price, command.nonProfit());
        price = applyPromo(price, command, monthsActive, today);
        price = applyLateFees(price, command.overdueDays());

        return price.orZeroIfNegative();
    }

    private static int monthsActive(LocalDate signupDate, LocalDate today) {
        return (today.getYear() - signupDate.getYear()) * 12 + (today.getMonthValue() - signupDate.getMonthValue());
    }

    private static Money applyLoyaltyDiscount(Money basePrice, int monthsActive) {
        if (monthsActive > LOYALTY_TIER_2_MONTHS) {
            return basePrice.minus(basePrice.times(LOYALTY_TIER_2_DISCOUNT));
        }
        if (monthsActive > LOYALTY_TIER_1_MONTHS) {
            return basePrice.minus(basePrice.times(LOYALTY_TIER_1_DISCOUNT));
        }
        return basePrice;
    }

    private static Money applyNonProfitDiscount(Money price, boolean nonProfit) {
        return nonProfit ? price.times(NON_PROFIT_FACTOR) : price;
    }

    private static Money applyPromo(Money price, BillingCommand command, int monthsActive, LocalDate today) {
        if (!command.hasPromoCode()) {
            return price;
        }
        String promo = command.promoCode();
        if (promo.equalsIgnoreCase(BLACK_FRIDAY_CODE) && today.getMonthValue() == BLACK_FRIDAY_MONTH) {
            return price.minus(BLACK_FRIDAY_DISCOUNT);
        }
        if (promo.equalsIgnoreCase(WELCOME_CODE) && monthsActive <= WELCOME_MAX_MONTHS) {
            return price.times(WELCOME_FACTOR);
        }
        return price;
    }

    private static Money applyLateFees(Money price, int overdueDays) {
        if (overdueDays <= 0) {
            return price;
        }
        Money penalty = price.times(LATE_PENALTY_RATE);
        Money dailyInterest = price.times(LATE_DAILY_INTEREST_RATE.multiply(BigDecimal.valueOf(overdueDays)));
        return price.plus(penalty).plus(dailyInterest);
    }
}
