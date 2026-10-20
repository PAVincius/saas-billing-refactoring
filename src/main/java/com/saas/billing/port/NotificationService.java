package com.saas.billing.port;

import com.saas.billing.domain.Money;

public interface NotificationService {

    /** @throws BillingInfrastructureException se o envio falhar. */
    void sendInvoice(String email, Money amount);
}
