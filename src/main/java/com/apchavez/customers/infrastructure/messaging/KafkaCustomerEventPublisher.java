package com.apchavez.customers.infrastructure.messaging;

import com.apchavez.customers.domain.event.CustomerEvent;
import com.apchavez.customers.domain.port.CustomerEventPublisherPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "spring.kafka.producer.bootstrap-servers")
public class KafkaCustomerEventPublisher implements CustomerEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaCustomerEventPublisher.class);
    private static final String TOPIC = "customer-events";

    private final ReactiveKafkaProducerTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaCustomerEventPublisher(ReactiveKafkaProducerTemplate<String, String> kafkaTemplate,
                                        ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(CustomerEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(json -> kafkaTemplate.send(TOPIC, event.customer().id().toString(), json))
                .doOnSuccess(r -> log.info("Event published: type={}, customerId={}",
                        event.eventType(), event.customer().id()))
                .doOnError(e -> log.error("Failed to publish event: type={}", event.eventType(), e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
