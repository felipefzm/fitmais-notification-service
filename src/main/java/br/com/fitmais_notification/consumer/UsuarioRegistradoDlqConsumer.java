package br.com.fitmais_notification.consumer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import br.com.fitmais_notification.config.RabbitMQConfig;
import br.com.fitmais_notification.dto.UsuarioRegistradoEvent;

@Component
public class UsuarioRegistradoDlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(UsuarioRegistradoDlqConsumer.class);

    // Loga o payload da mensagem que caiu na DLQ junto com o erro original que causou a falha.
    @RabbitListener(queues = RabbitMQConfig.DLQ)
    public void receber(@Payload UsuarioRegistradoEvent event, @Headers Map<String, Object> headers) {
        log.error("Mensagem recebida na DLQ.\nPayload: {}\nErro original: {}\nHorário: {}",
                event, extrairErroOriginal(headers), LocalDateTime.now());
    }

    // x-exception-message vem do RepublishMessageRecoverer (falha de aplicação, com a mensagem real da exception).
    // x-death é o fallback nativo do RabbitMQ 
    @SuppressWarnings("unchecked")
    private String extrairErroOriginal(Map<String, Object> headers) {
        Object exceptionMessage = headers.get("x-exception-message");
        if (exceptionMessage != null) {
            return exceptionMessage.toString();
        }

        List<Map<String, ?>> xDeath = (List<Map<String, ?>>) headers.get("x-death");
        if (xDeath == null || xDeath.isEmpty()) {
            return "desconhecido";
        }

        Object reason = xDeath.get(0).get("reason");
        return reason != null ? reason.toString() : "desconhecido";
    }
}
