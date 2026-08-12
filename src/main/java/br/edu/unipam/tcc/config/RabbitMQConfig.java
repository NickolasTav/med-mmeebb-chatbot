package br.edu.unipam.tcc.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchanges.med-direct:ex.med.direct}")
    private String directExchange;

    @Value("${rabbitmq.exchanges.dead-letter:ex.med.dlx}")
    private String dlxExchange;

    @Value("${rabbitmq.queues.uaizap-inbound:q.uaizap.inbound.messages}")
    private String inboundQueue;

    @Value("${rabbitmq.queues.mmeebb-evaluations:q.med.mmeebb.evaluations}")
    private String evaluationsQueue;

    @Value("${rabbitmq.queues.uaizap-outbound:q.uaizap.outbound.dispatches}")
    private String outboundQueue;

    @Value("${rabbitmq.queues.dead-letter:q.med.deadletter}")
    private String dlqQueue;

    @Value("${rabbitmq.routing-keys.inbound:rk.uaizap.inbound}")
    private String inboundRoutingKey;

    @Value("${rabbitmq.routing-keys.evaluations:rk.med.evaluations}")
    private String evaluationsRoutingKey;

    @Value("${rabbitmq.routing-keys.outbound:rk.uaizap.outbound}")
    private String outboundRoutingKey;

    @Value("${rabbitmq.routing-keys.dead-letter:rk.med.dlq}")
    private String dlqRoutingKey;

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(directExchange, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlxExchange, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(dlqQueue).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(dlqRoutingKey);
    }

    @Bean
    public Queue inboundQueue() {
        return QueueBuilder.durable(inboundQueue)
                .withArgument("x-dead-letter-exchange", dlxExchange)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding inboundBinding() {
        return BindingBuilder.bind(inboundQueue()).to(exchange()).with(inboundRoutingKey);
    }

    @Bean
    public Queue evaluationsQueue() {
        return QueueBuilder.durable(evaluationsQueue)
                .withArgument("x-dead-letter-exchange", dlxExchange)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding evaluationsBinding() {
        return BindingBuilder.bind(evaluationsQueue()).to(exchange()).with(evaluationsRoutingKey);
    }

    @Bean
    public Queue outboundQueue() {
        return QueueBuilder.durable(outboundQueue)
                .withArgument("x-dead-letter-exchange", dlxExchange)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding outboundBinding() {
        return BindingBuilder.bind(outboundQueue()).to(exchange()).with(outboundRoutingKey);
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

