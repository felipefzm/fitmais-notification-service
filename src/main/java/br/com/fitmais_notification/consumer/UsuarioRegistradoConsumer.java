package br.com.fitmais_notification.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.com.fitmais_notification.config.RabbitMQConfig;
import br.com.fitmais_notification.dto.UsuarioRegistradoEvent;
import br.com.fitmais_notification.service.EmailService;

@Component
public class UsuarioRegistradoConsumer {

    private static final Logger log = LoggerFactory.getLogger(UsuarioRegistradoConsumer.class);

    private final EmailService emailService;

    public UsuarioRegistradoConsumer(
            EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void receber(UsuarioRegistradoEvent event) {
        log.info("Evento recebido da fila '{}': usuarioId={}, username='{}', email='{}', registradoEm={}",
                RabbitMQConfig.QUEUE, event.usuarioId(), event.username(), event.email(), event.registradoEm());

        try {
            emailService.enviarBoasVindas(
                    event.username(),
                    event.email()
            );
        } catch (RuntimeException e) {
            log.error("Falha ao processar evento de usuário registrado (usuarioId={}, email='{}'): {}",
                    event.usuarioId(), event.email(), e.getMessage(), e);
            throw e;
        }
    }

}