package com.apchavez.products.infrastructure.messaging;

import com.apchavez.products.domain.event.ProductEvent;
import com.apchavez.products.domain.event.ProductEventType;
import com.apchavez.products.domain.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaProductEventPublisherTest {

    @Mock
    private KafkaSender<String, String> kafkaSender;

    private KafkaProductEventPublisher publisher;

    private static final Product PRODUCT =
            new Product(1, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true);

    @BeforeEach
    void setUp() {
        publisher = new KafkaProductEventPublisher(kafkaSender);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_shouldSendJsonToKafkaTopic() {
        ProductEvent event = ProductEvent.of(ProductEventType.PRODUCT_CREATED, PRODUCT);
        SenderResult<Void> senderResult = mock(SenderResult.class);

        when(kafkaSender.send(any(Publisher.class))).thenReturn(Flux.just(senderResult));

        StepVerifier.create(publisher.publish(event))
                .verifyComplete();

        verify(kafkaSender).send(any(Publisher.class));
    }

    @Test
    void publish_shouldCompleteGracefully_whenKafkaFails() {
        ProductEvent event = ProductEvent.of(ProductEventType.PRODUCT_CREATED, PRODUCT);

        when(kafkaSender.send(any(Publisher.class)))
                .thenReturn(Flux.error(new RuntimeException("Kafka unavailable")));

        StepVerifier.create(publisher.publish(event))
                .verifyComplete();
    }
}
