package com.saas.billing.port;

/**
 * Falha de infraestrutura (banco, gateway, SMTP) vista pelo domínio.
 * A mensagem é a da causa original para preservar o texto do log legado.
 */
public class BillingInfrastructureException extends RuntimeException {

    public BillingInfrastructureException(Throwable cause) {
        super(cause.getMessage(), cause);
    }

    public BillingInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
