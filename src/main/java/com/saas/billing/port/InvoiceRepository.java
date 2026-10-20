package com.saas.billing.port;

import com.saas.billing.domain.InvoiceStatus;
import com.saas.billing.domain.Money;

public interface InvoiceRepository {

    /** @throws BillingInfrastructureException se o armazenamento falhar. */
    void save(String customerId, Money amount, InvoiceStatus status);
}
