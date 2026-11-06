package br.com.fiap.feedback.commons;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;

/** Envio de e-mail texto via SMTP (Mailpit no cloudlab; SES/ACS/OCI Email em nuvem pública). */
public class EnviadorEmail {

    private final Session sessao;
    private final String remetente;

    public EnviadorEmail(String host, int porta, String remetente) {
        Properties p = new Properties();
        p.put("mail.smtp.host", host);
        p.put("mail.smtp.port", Integer.toString(porta));
        p.put("mail.smtp.connectiontimeout", "5000");
        p.put("mail.smtp.timeout", "10000");
        this.sessao = Session.getInstance(p);
        this.remetente = remetente;
    }

    public static EnviadorEmail doAmbiente() {
        return new EnviadorEmail(Config.obrigatorio("SMTP_HOST"), Config.inteiro("SMTP_PORT", 1025),
                Config.obrigatorio("EMAIL_REMETENTE"));
    }

    public void enviar(List<String> destinatarios, String assunto, String corpo) {
        try {
            MimeMessage msg = new MimeMessage(sessao);
            msg.setFrom(new InternetAddress(remetente));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(String.join(",", destinatarios)));
            msg.setSubject(assunto, "UTF-8");
            msg.setText(corpo, "UTF-8");
            Transport.send(msg);
        } catch (MessagingException e) {
            throw new IllegalStateException("Falha ao enviar e-mail: " + e.getMessage(), e);
        }
    }
}
