package com.saas.billing.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Entrada de uma cobrança mensal. Substitui a lista de 8 parâmetros soltos
 * do método legado. {@code planType} é preservado da assinatura original,
 * mas continua sem influenciar o cálculo (como no legado).
 */
public record BillingCommand(
        String customerId,
        String planType,
        Money basePrice,
        LocalDate signupDate,
        int overdueDays,
        String promoCode,
        boolean nonProfit,
        String paymentCardToken) {

    public BillingCommand {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(basePrice, "basePrice");
        Objects.requireNonNull(signupDate, "signupDate");
    }

    public boolean hasPromoCode() {
        return promoCode != null && !promoCode.trim().isEmpty();
    }
}
