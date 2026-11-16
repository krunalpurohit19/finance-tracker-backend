package com.financetracker.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Plain-text email over SMTP (Amazon SES and Resend both speak it): set MAIL_HOST, MAIL_USERNAME,
 * MAIL_PASSWORD and MAIL_FROM. With no MAIL_HOST (local development) the message is logged instead.
 */
@Component
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;

    public Mailer(ObjectProvider<JavaMailSender> sender, @Value("${spring.mail.host:}") String host,
                  @Value("${app.mail.from}") String from) {
        this.sender = sender;
        this.host = host;
        this.from = from;
    }

    /** Never throws: a mail outage must not fail sign-up or leak whether an address has an account. */
    public void send(String to, String subject, String text) {
        JavaMailSender mail = host.isBlank() ? null : sender.getIfAvailable();
        if (mail == null) {
            log.warn("MAIL_HOST is not set; email not sent. To: {} | {} | {}", to, subject, text.replace('\n', ' '));
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mail.send(message);
        } catch (RuntimeException e) {
            log.error("Email to {} failed: {}", to, e.getMessage());
        }
    }
}
