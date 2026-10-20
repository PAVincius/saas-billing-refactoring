package com.saas.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saas.billing.adapter.TokenPrefixPaymentGateway;
import com.saas.billing.domain.BillingCommand;
import com.saas.billing.domain.Customer;
import com.saas.billing.domain.InvoiceStatus;
import com.saas.billing.domain.Money;
import com.saas.billing.port.BillingInfrastructureException;
import com.saas.billing.port.CustomerRepository;
import com.saas.billing.port.PaymentGateway;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Rede de segurança do comportamento de negócio. Roda 100% em memória:
 * sem banco, sem SMTP, sem relógio real.
 */
class SubscriptionBillingServiceTest {

    private static final LocalDate OCTOBER_20 = LocalDate.of(2026, 10, 20);

    private final List<String> savedInvoices = new ArrayList<>();
    private final List<String> sentEmails = new ArrayList<>();
    private Customer customer = new Customer("ana@ong.org", "ACTIVE");
    private PaymentGateway payments = new TokenPrefixPaymentGateway();

    @BeforeEach
    void setUp() {
        savedInvoices.clear();
        sentEmails.clear();
    }

    private static Clock clockAt(LocalDate date) {
        return Clock.fixed(Instant.parse(date + "T12:00:00Z"), ZoneOffset.UTC);
    }

    private SubscriptionBillingService serviceAt(LocalDate today) {
        CustomerRepository repo = id -> "c1".equals(id) ? Optional.of(customer) : Optional.empty();
        return new SubscriptionBillingService(
                repo,
                (id, amount, status) -> savedInvoices.add(id + "|" + amount + "|" + status),
                payments,
                (email, amount) -> sentEmails.add(email + "|" + amount),
                clockAt(today));
    }

    private static BillingCommand command(String customerId, Money base, LocalDate signup, int overdueDays,
                                          String promo, boolean nonProfit, String token) {
        return new BillingCommand(customerId, "PRO", base, signup, overdueDays, promo, nonProfit, token);
    }

    private static BillingCommand simple(String token) {
        return command("c1", Money.of("100.00"), OCTOBER_20, 0, null, false, token);
    }

    @Test
    void unknownCustomerProducesNoLogsInvoiceOrEmail() {
        List<String> logs = serviceAt(OCTOBER_20).processMonthlyBilling(
                command("ghost", Money.of("100.00"), OCTOBER_20, 0, null, false, "tok_1"));

        assertTrue(logs.isEmpty());
        assertTrue(savedInvoices.isEmpty());
        assertTrue(sentEmails.isEmpty());
    }

    @Test
    void inactiveCustomerCancelsBillingWithoutInvoiceOrEmail() {
        customer = new Customer("ana@ong.org", "SUSPENDED");

        List<String> logs = serviceAt(OCTOBER_20).processMonthlyBilling(simple("tok_1"));

        assertEquals(List.of("Cliente inativo. Cobrança cancelada: c1"), logs);
        assertTrue(savedInvoices.isEmpty());
        assertTrue(sentEmails.isEmpty());
    }

    @Test
    void statusIsComparedIgnoringCase() {
        customer = new Customer("ana@ong.org", "active");

        serviceAt(OCTOBER_20).processMonthlyBilling(simple("tok_1"));

        assertEquals(List.of("c1|100.00|PAID"), savedInvoices);
    }

    @Test
    void successfulPaymentSavesPaidInvoiceAndNotifiesCustomer() {
        List<String> logs = serviceAt(OCTOBER_20).processMonthlyBilling(simple("tok_abc"));

        assertEquals(List.of(
                "Pagamento processado com sucesso: R$ 100.00",
                "E-mail disparado para: ana@ong.org"), logs);
        assertEquals(List.of("c1|100.00|" + InvoiceStatus.PAID), savedInvoices);
        assertEquals(List.of("ana@ong.org|100.00"), sentEmails);
    }

    @Test
    void declinedPaymentStillSavesFailedInvoiceAndNotifies() {
        List<String> logs = serviceAt(OCTOBER_20).processMonthlyBilling(simple("xyz"));

        assertEquals(List.of(
                "Falha no pagamento do cartão para: c1",
                "E-mail disparado para: ana@ong.org"), logs);
        assertEquals(List.of("c1|100.00|" + InvoiceStatus.FAILED), savedInvoices);
        assertEquals(1, sentEmails.size());
    }

    @Test
    void nullCardTokenIsADeclinedPayment() {
        serviceAt(OCTOBER_20).processMonthlyBilling(simple(null));

        assertEquals(List.of("c1|100.00|" + InvoiceStatus.FAILED), savedInvoices);
    }

    @Test
    void infrastructureFailureIsReportedInLogsAndStopsBeforeNotification() {
        CustomerRepository repo = id -> Optional.of(customer);
        SubscriptionBillingService service = new SubscriptionBillingService(
                repo,
                (id, amount, status) -> {
                    throw new BillingInfrastructureException(new SQLException("db down"));
                },
                payments,
                (email, amount) -> sentEmails.add(email),
                clockAt(OCTOBER_20));

        List<String> logs = service.processMonthlyBilling(simple("tok_1"));

        assertEquals(List.of(
                "Pagamento processado com sucesso: R$ 100.00",
                "Erro fatal no processamento: db down"), logs);
        assertTrue(sentEmails.isEmpty());
    }

    @Test
    void unexpectedBugsAreNotMaskedAsBillingLogs() {
        payments = (token, amount) -> {
            throw new IllegalStateException("bug no gateway");
        };

        assertThrows(IllegalStateException.class, () -> serviceAt(OCTOBER_20).processMonthlyBilling(simple("tok_1")));
        assertTrue(savedInvoices.isEmpty());
    }

    @Test
    void dateSensitiveRulesFollowTheInjectedClockNotTheSystemClock() {
        BillingCommand blackFriday = command("c1", Money.of("100.00"), OCTOBER_20, 0, "BLACKFRIDAY", false, "tok_1");

        serviceAt(LocalDate.of(2026, 11, 27)).processMonthlyBilling(blackFriday);
        serviceAt(LocalDate.of(2026, 12, 1)).processMonthlyBilling(blackFriday);

        assertEquals(List.of("c1|70.00|PAID", "c1|100.00|PAID"), savedInvoices);
    }

    /** Cada linha: meses de casa, ONG, promo, dias de atraso, "hoje", base, esperado. */
    @ParameterizedTest(name = "{0}m ong={1} promo={2} atraso={3} hoje={4} base={5} -> {6}")
    @CsvSource(value = {
            "0,  false, NONE,        0,  2026-10-20, 100.00, 100.00",
            "12, false, NONE,        0,  2026-10-20, 100.00, 100.00",
            "13, false, NONE,        0,  2026-10-20, 100.00,  90.00",
            "24, false, NONE,        0,  2026-10-20, 100.00,  90.00",
            "25, false, NONE,        0,  2026-10-20, 100.00,  85.00",
            "0,  true,  NONE,        0,  2026-10-20, 100.00,  70.00",
            "13, true,  NONE,        0,  2026-10-20, 100.00,  63.00",
            "1,  false, WELCOME10,   0,  2026-10-20, 100.00,  90.00",
            "2,  false, WELCOME10,   0,  2026-10-20, 100.00, 100.00",
            "0,  false, welcome10,   0,  2026-10-20, 100.00,  90.00",
            "0,  false, BLACKFRIDAY, 0,  2026-11-27, 100.00,  70.00",
            "0,  false, BLACKFRIDAY, 0,  2026-10-27, 100.00, 100.00",
            "0,  false, BLACKFRIDAY, 0,  2026-11-27,  20.00,   0.00",
            "0,  false, INVALID,     0,  2026-10-20, 100.00, 100.00",
            "0,  false, '   ',       0,  2026-10-20, 100.00, 100.00",
            "0,  false, NONE,       10,  2026-10-20, 100.00, 103.00",
            "13, true,  WELCOME10,  30,  2026-10-20, 100.00,  66.15",
    }, nullValues = "NONE")
    void priceRulesMatchLegacyBehavior(int monthsActive, boolean nonProfit, String promo, int overdueDays,
                                       LocalDate today, String base, String expected) {
        LocalDate signup = today.withDayOfMonth(1).minusMonths(monthsActive);

        serviceAt(today).processMonthlyBilling(
                command("c1", Money.of(base), signup, overdueDays, promo, nonProfit, "tok_1"));

        assertEquals(List.of("c1|" + expected + "|PAID"), savedInvoices);
    }
}
