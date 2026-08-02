package br.com.fitmais_notification.config;

import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE = "usuario.criado.queue";
    public static final String DLQ = "usuario.criado.dlq";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // Republica a mensagem na DLQ com o erro real (x-exception-message) nos headers, em vez de deixar
    // o dead-letter nativo do RabbitMQ acontecer, que só registra um motivo genérico ("rejected") no x-death.
    @Bean
    public RetryOperationsInterceptor retryOperationsInterceptor(RabbitTemplate rabbitTemplate) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(new RepublishMessageRecoverer(rabbitTemplate, "", DLQ))
                .build();
    }

    // Precisa se chamar "rabbitListenerContainerFactory" pra substituir a factory padrão do Spring Boot
    // (é o nome que o @RabbitListener usa por convenção quando nenhuma factory é indicada explicitamente).
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            RetryOperationsInterceptor retryOperationsInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(retryOperationsInterceptor);
        // A api pode declarar a fila depois deste serviço subir (ordem não garantida no
        // docker-compose); sem isso o container do listener falharia ao não achá-la de imediato.
        factory.setMissingQueuesFatal(false);
        return factory;
    }
}
