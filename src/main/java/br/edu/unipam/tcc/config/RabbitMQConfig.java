package br.edu.unipam.tcc.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração simplificada de mensageria RabbitMQ (Padrão Direct-to-Queue / Chatbot).
 * Utiliza a Default Exchange do AMQP para roteamento direto por nome de fila,
 * eliminando boilerplate de routing keys e exchanges intermediárias.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.queues.inbound:q.uaizap.inbound}")
    private String inboundQueue;

    @Value("${rabbitmq.queues.outbound:q.uaizap.outbound}")
    private String outboundQueue;

    @Value("${rabbitmq.queues.dlq:q.med.dlq}")
    private String dlqQueue;

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(dlqQueue).build();
    }

    @Bean
    public Queue inboundQueue() {
        return QueueBuilder.durable(inboundQueue)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", dlqQueue)
                .build();
    }

    @Bean
    public Queue outboundQueue() {
        return QueueBuilder.durable(outboundQueue)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", dlqQueue)
                .build();
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    public org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory factory =
                new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setObservationEnabled(true);
        return factory;
    }
}


