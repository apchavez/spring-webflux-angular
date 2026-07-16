package com.apchavez.products.infrastructure.messaging;

import com.apchavez.products.domain.event.ProductEvent;
import com.apchavez.products.domain.port.ProductEventPublisherPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Component
@ConditionalOnProperty(name = "spring.kafka.producer.bootstrap-servers")
public class KafkaProductEventPublisher implements ProductEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaProductEventPublisher.class);
    private static final String TOPIC = "product-events";

    // Dedicated instance rather than an autowired Spring bean: this is a reactive,
    // WebEnvironment.NONE-compatible app with no guaranteed ObjectMapper bean in every context
    // (see the identical rationale in ProductPersistenceAdapter), and ProductEvent
    // (de)serialization needs no Spring codecs.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KafkaSender<String, String> kafkaSender;

    public KafkaProductEventPublisher(KafkaSender<String, String> kafkaSender) {
        this.kafkaSender = kafkaSender;
    }

    @Override
    public Mono<Void> publish(ProductEvent event) {
        return Mono.fromCallable(() -> OBJECT_MAPPER.writeValueAsString(event))
                .flatMap(json -> kafkaSender.send(Mono.just(SenderRecord.create(
                        new ProducerRecord<>(TOPIC, event.product().id().toString(), json), null))).next())
                .doOnSuccess(r -> log.info("Event published: type={}, productId={}",
                        event.eventType(), event.product().id()))
                .doOnError(e -> log.error("Failed to publish event: type={}", event.eventType(), e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
