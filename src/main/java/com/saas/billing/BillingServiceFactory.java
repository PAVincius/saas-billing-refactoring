package com.saas.billing;

import com.saas.billing.adapter.JdbcCustomerRepository;
import com.saas.billing.adapter.JdbcInvoiceRepository;
import com.saas.billing.adapter.SmtpNotificationService;
import com.saas.billing.adapter.TokenPrefixPaymentGateway;
import java.time.Clock;
import java.util.Map;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Raiz de composição: único lugar que conhece as implementações concretas.
 * Credenciais vêm do ambiente, nunca do código-fonte.
 */
public final class BillingServiceFactory {

    private static final int DEFAULT_SMTP_PORT = 587;

    private BillingServiceFactory() {
    }

    public static SubscriptionBillingService fromEnvironment(Map<String, String> env) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(require(env, "BILLING_DB_URL"));
        dataSource.setUser(require(env, "BILLING_DB_USER"));
        dataSource.setPassword(require(env, "BILLING_DB_PASSWORD"));

        int smtpPort = Integer.parseInt(env.getOrDefault("BILLING_SMTP_PORT", String.valueOf(DEFAULT_SMTP_PORT)));

        return new SubscriptionBillingService(
                new JdbcCustomerRepository(dataSource),
                new JdbcInvoiceRepository(dataSource),
                new TokenPrefixPaymentGateway(),
                new SmtpNotificationService(require(env, "BILLING_SMTP_HOST"), smtpPort, require(env, "BILLING_SMTP_FROM")),
                Clock.systemDefaultZone());
    }

    private static String require(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável de ambiente obrigatória ausente: " + name);
        }
        return value;
    }
}
