package com.saas.billing.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BillingCommandTest {

    private static BillingCommand withPromo(String promoCode) {
        return new BillingCommand("c1", "PRO", Money.of("100.00"), LocalDate.of(2024, 1, 15), 0, promoCode, false, "tok_1");
    }

    @Test
    void nullBlankAndWhitespacePromoCodesAreNotPromos() {
        assertFalse(withPromo(null).hasPromoCode());
        assertFalse(withPromo("").hasPromoCode());
        assertFalse(withPromo("   ").hasPromoCode());
    }

    @Test
    void filledPromoCodeIsAPromo() {
        assertTrue(withPromo("WELCOME10").hasPromoCode());
    }

    @Test
    void requiresCustomerPriceAndSignupDate() {
        LocalDate d = LocalDate.of(2024, 1, 15);
        assertThrows(NullPointerException.class, () -> new BillingCommand(null, "PRO", Money.ZERO, d, 0, null, false, null));
        assertThrows(NullPointerException.class, () -> new BillingCommand("c1", "PRO", null, d, 0, null, false, null));
        assertThrows(NullPointerException.class, () -> new BillingCommand("c1", "PRO", Money.ZERO, null, 0, null, false, null));
    }
}
