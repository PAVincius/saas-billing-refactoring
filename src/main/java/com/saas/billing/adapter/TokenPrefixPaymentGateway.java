package com.saas.billing.adapter;

import com.saas.billing.domain.Money;
import com.saas.billing.port.PaymentGateway;

/**
 * Gateway simulado herdado do legado: aceita qualquer token que comece com
 * "tok_". Será substituído pela integração real com o provedor de cartão.
 */
public class TokenPrefixPaymentGateway implements PaymentGateway {

    private static final String ACCEPTED_PREFIX = "tok_";

    @Override
    public boolean charge(String cardToken, Money amount) {
        return cardToken != null && cardToken.startsWith(ACCEPTED_PREFIX);
    }
}
