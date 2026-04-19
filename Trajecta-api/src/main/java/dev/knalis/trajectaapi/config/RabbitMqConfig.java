package dev.knalis.trajectaapi.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static dev.knalis.trajectaapi.messaging.RabbitTopology.*;

@Configuration
public class RabbitMqConfig {

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public DirectExchange telemetryExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue telemetryParseQueue() {
        return new Queue(PARSE_QUEUE, true);
    }

    @Bean
    public Queue telemetryResultsQueue() {
        return new Queue(RESULTS_QUEUE, true);
    }

    @Bean
    public Binding telemetryParseBinding(Queue telemetryParseQueue, DirectExchange telemetryExchange) {
        return BindingBuilder.bind(telemetryParseQueue).to(telemetryExchange).with(PARSE_ROUTING_KEY);
    }

    @Bean
    public Binding telemetryResultsBinding(Queue telemetryResultsQueue, DirectExchange telemetryExchange) {
        return BindingBuilder.bind(telemetryResultsQueue).to(telemetryExchange).with(RESULTS_ROUTING_KEY);
    }
}

