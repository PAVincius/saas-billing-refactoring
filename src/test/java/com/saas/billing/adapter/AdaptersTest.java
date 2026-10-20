package com.saas.billing.adapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saas.billing.domain.Money;
import com.saas.billing.port.BillingInfrastructureException;
import org.junit.jupiter.api.Test;

/** Adaptadores que não exigem infraestrutura externa para serem exercitados. */
class AdaptersTest {

    private final TokenPrefixPaymentGateway gateway = new TokenPrefixPaymentGateway();

    @Test
    void gatewayAcceptsOnlyTokensWithTokPrefix() {
        assertTrue(gateway.charge("tok_123", Money.of("10.00")));
        assertFalse(gateway.charge("card_123", Money.of("10.00")));
        assertFalse(gateway.charge("", Money.of("10.00")));
        assertFalse(gateway.charge(null, Money.of("10.00")));
    }

    @Test
    void smtpAdapterBuildsMessageWithoutOpeningAConnection() {
        SmtpNotificationService smtp = new SmtpNotificationService("smtp.invalid", 587, "billing@empresa.com");

        assertDoesNotThrow(() -> smtp.sendInvoice("ana@ong.org", Money.of("90.00")));
    }

    @Test
    void smtpAdapterWrapsInvalidAddressAsInfrastructureFailure() {
        SmtpNotificationService smtp = new SmtpNotificationService("smtp.invalid", 587, "billing@empresa.com");

        assertThrows(BillingInfrastructureException.class, () -> smtp.sendInvoice("not-an-email", Money.of("90.00")));
    }
}
