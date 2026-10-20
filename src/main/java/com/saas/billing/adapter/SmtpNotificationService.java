package com.saas.billing.adapter;

import com.saas.billing.domain.Money;
import com.saas.billing.port.BillingInfrastructureException;
import com.saas.billing.port.NotificationService;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

/**
 * Monta o recibo por e-mail. COMPORTAMENTO LEGADO PRESERVADO: o legado nunca
 * chamou {@code send()} (linha comentada), então o e-mail é montado e validado
 * mas não é transmitido. Habilitar o envio é decisão de negócio fora do escopo
 * da refatoração (ver RELATORIO.md).
 */
public class SmtpNotificationService implements NotificationService {

    private final String host;
    private final int port;
    private final String from;

    public SmtpNotificationService(String host, int port, String from) {
        this.host = host;
        this.port = port;
        this.from = from;
    }

    @Override
    public void sendInvoice(String email, Money amount) {
        try {
            SimpleEmail message = new SimpleEmail();
            message.setHostName(host);
            message.setSmtpPort(port);
            message.setFrom(from);
            message.setSubject("Sua Fatura Mensal");
            message.setMsg("Olá! Sua fatura foi processada no valor de R$ " + amount);
            message.addTo(email);
        } catch (EmailException e) {
            throw new BillingInfrastructureException(e);
        }
    }
}
