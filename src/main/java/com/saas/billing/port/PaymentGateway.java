package com.saas.billing.port;

import com.saas.billing.domain.Money;

public interface PaymentGateway {

    /** @return true se a cobrança foi aceita, false se foi recusada. */
    boolean charge(String cardToken, Money amount);
}
