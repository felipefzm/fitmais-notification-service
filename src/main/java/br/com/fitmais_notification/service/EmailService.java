package br.com.fitmais_notification.service;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${fitmais.frontend.url}")
    private String frontendUrl;

    public EmailService(
            JavaMailSender mailSender,
            TemplateEngine templateEngine
    ) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    public void enviarBoasVindas(String nome, String email) {

        log.info("Iniciando envio de e-mail de boas-vindas para='{}' remetente='{}'", email, from);

        try {

            Context context = new Context();

            context.setVariable("nome", nome);
            context.setVariable("url", frontendUrl);

            String html = templateEngine.process(
                    "email-boas-vindas", context);

            MimeMessage message =
                    mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message,
                            true,
                            StandardCharsets.UTF_8.name()
                    );

            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("Bem-vindo ao FitMais 🚀");

            helper.setText(html, true);

            mailSender.send(message);

            log.info("E-mail de boas-vindas enviado com sucesso para='{}'", email);

        } catch (MessagingException e) {
            log.error("Falha ao montar e-mail para='{}': {}", email, e.getMessage(), e);
            throw new RuntimeException(
                    "Erro ao enviar e-mail.",
                    e
            );
        } catch (MailException e) {
            log.error("Falha ao enviar e-mail via SMTP para='{}': {}", email, e.getMessage(), e);
            throw e;
        }
    }
}