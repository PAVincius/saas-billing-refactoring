package com.saas.billing;

import com.saas.billing.domain.BillingCommand;
import com.saas.billing.domain.Customer;
import com.saas.billing.domain.InvoiceStatus;
import com.saas.billing.domain.Money;
import com.saas.billing.port.CustomerRepository;
import com.saas.billing.port.InvoiceRepository;
import com.saas.billing.port.NotificationService;
import com.saas.billing.port.PaymentGateway;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubscriptionBillingService {

    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final PaymentGateway payments;
    private final NotificationService notifications;
    private final Clock clock;

    public SubscriptionBillingService(CustomerRepository customers, InvoiceRepository invoices,
                                      PaymentGateway payments, NotificationService notifications, Clock clock) {
        this.customers = customers;
        this.invoices = invoices;
        this.payments = payments;
        this.notifications = notifications;
        this.clock = clock;
    }

    public List<String> processMonthlyBilling(BillingCommand command) {

        List<String> logs = new ArrayList<>();

        LocalDate today = LocalDate.now(clock);

        Money calculatedPrice = calculatePrice(command, today);

        String customerId = command.customerId();

        try {
            Optional<Customer> found = customers.findById(customerId);

            if (found.isPresent()) {
                Customer customer = found.get();

                if (!customer.isActive()) {
                    logs.add("Cliente inativo. Cobrança cancelada: " + customerId);
                    return logs;
                }

                boolean paymentSuccess = payments.charge(command.paymentCardToken(), calculatedPrice);
                if (paymentSuccess) {
                    logs.add("Pagamento processado com sucesso: R$ " + calculatedPrice);
                } else {
                    logs.add("Falha no pagamento do cartão para: " + customerId);
                }

                invoices.save(customerId, calculatedPrice, paymentSuccess ? InvoiceStatus.PAID : InvoiceStatus.FAILED);

                notifications.sendInvoice(customer.email(), calculatedPrice);

                logs.add("E-mail disparado para: " + customer.email());
            }
        } catch (Exception e) {
            logs.add("Erro fatal no processamento: " + e.getMessage());
        }

        return logs;
    }

    private Money calculatePrice(BillingCommand command, LocalDate today) {
        LocalDate signupDate = command.signupDate();
        int monthsActive = (today.getYear() - signupDate.getYear()) * 12 + (today.getMonthValue() - signupDate.getMonthValue());

        Money basePrice = command.basePrice();
        Money calculatedPrice = basePrice;

        if (monthsActive > 24) {
            calculatedPrice = calculatedPrice.minus(basePrice.times(new BigDecimal("0.15"))); // 15% após 2 anos
        } else if (monthsActive > 12) {
            calculatedPrice = calculatedPrice.minus(basePrice.times(new BigDecimal("0.10"))); // 10% após 1 ano
        }

        if (command.nonProfit()) {
            calculatedPrice = calculatedPrice.times(new BigDecimal("0.70")); // 30% off para ONGs
        }

        if (command.hasPromoCode()) {
            String promoCode = command.promoCode();
            if (promoCode.equalsIgnoreCase("BLACKFRIDAY") && today.getMonthValue() == 11) {
                calculatedPrice = calculatedPrice.minus(Money.of("30.00"));
            } else if (promoCode.equalsIgnoreCase("WELCOME10") && monthsActive <= 1) {
                calculatedPrice = calculatedPrice.times(new BigDecimal("0.90"));
            }
        }

        if (command.overdueDays() > 0) {
            Money penalty = calculatedPrice.times(new BigDecimal("0.02"));
            Money dailyInterest = calculatedPrice.times(new BigDecimal("0.001").multiply(BigDecimal.valueOf(command.overdueDays())));
            calculatedPrice = calculatedPrice.plus(penalty).plus(dailyInterest);
        }

        return calculatedPrice.orZeroIfNegative();
    }
}
