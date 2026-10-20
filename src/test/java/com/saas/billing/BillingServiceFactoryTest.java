package com.saas.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BillingServiceFactoryTest {

    private static Map<String, String> fullEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("BILLING_DB_URL", "jdbc:postgresql://localhost:5432/saas_db");
        env.put("BILLING_DB_USER", "app");
        env.put("BILLING_DB_PASSWORD", "from-env");
        env.put("BILLING_SMTP_HOST", "smtp.empresa.com");
        env.put("BILLING_SMTP_FROM", "billing@empresa.com");
        return env;
    }

    @Test
    void buildsServiceFromEnvironmentWithoutConnectingToAnything() {
        assertNotNull(BillingServiceFactory.fromEnvironment(fullEnv()));
    }

    @Test
    void failsFastNamingTheMissingVariable() {
        for (String name : new String[] {"BILLING_DB_URL", "BILLING_DB_USER", "BILLING_DB_PASSWORD",
                "BILLING_SMTP_HOST", "BILLING_SMTP_FROM"}) {
            Map<String, String> env = fullEnv();
            env.remove(name);

            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> BillingServiceFactory.fromEnvironment(env));

            assertEquals("Variável de ambiente obrigatória ausente: " + name, e.getMessage());
        }
    }

    @Test
    void blankVariableCountsAsMissing() {
        Map<String, String> env = fullEnv();
        env.put("BILLING_DB_PASSWORD", "   ");

        assertThrows(IllegalStateException.class, () -> BillingServiceFactory.fromEnvironment(env));
    }
}
