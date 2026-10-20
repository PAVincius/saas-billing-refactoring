package com.saas.billing;

import com.saas.billing.domain.BillingCommand;
import com.saas.billing.domain.Customer;
import com.saas.billing.domain.InvoiceStatus;
import com.saas.billing.domain.Money;
import com.saas.billing.domain.PricingCalculator;
import com.saas.billing.port.BillingInfrastructureException;
import com.saas.billing.port.CustomerRepository;
import com.saas.billing.port.InvoiceRepository;
import com.saas.billing.port.NotificationService;
import com.saas.billing.port.PaymentGateway;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Orquestra a cobrança mensal: precifica, cobra, registra a fatura e notifica. */
public class SubscriptionBillingService {

    private static final System.Logger LOG = System.getLogger(SubscriptionBillingService.class.getName());

    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final PaymentGateway payments;
    private final NotificationService notifications;
    private final PricingCalculator pricing;
    private final Clock clock;

    public SubscriptionBillingService(CustomerRepository customers, InvoiceRepository invoices,
                                      PaymentGateway payments, NotificationService notifications, Clock clock) {
        this.customers = customers;
        this.invoices = invoices;
        this.payments = payments;
        this.notifications = notifications;
        this.pricing = new PricingCalculator();
        this.clock = clock;
    }

    public List<String> processMonthlyBilling(BillingCommand command) {
        List<String> logs = new ArrayList<>();
        Money amount = pricing.calculate(command, LocalDate.now(clock));
        try {
            customers.findById(command.customerId())
                    .ifPresent(customer -> bill(customer, command, amount, logs));
        } catch (BillingInfrastructureException e) {
            LOG.log(System.Logger.Level.ERROR, "Falha de infraestrutura ao cobrar " + command.customerId(), e);
            logs.add("Erro fatal no processamento: " + e.getMessage());
        }
        return logs;
    }

    private void bill(Customer customer, BillingCommand command, Money amount, List<String> logs) {
        String customerId = command.customerId();
        if (!customer.isActive()) {
            logs.add("Cliente inativo. Cobrança cancelada: " + customerId);
            return;
        }
        boolean paid = payments.charge(command.paymentCardToken(), amount);
        logs.add(paid ? "Pagamento processado com sucesso: R$ " + amount : "Falha no pagamento do cartão para: " + customerId);
        invoices.save(customerId, amount, paid ? InvoiceStatus.PAID : InvoiceStatus.FAILED);
        notifications.sendInvoice(customer.email(), amount);
        logs.add("E-mail disparado para: " + customer.email());
    }
}
