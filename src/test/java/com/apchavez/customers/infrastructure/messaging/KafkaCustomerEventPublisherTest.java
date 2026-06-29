package com.apchavez.customers.infrastructure.messaging;

import com.apchavez.customers.domain.event.CustomerEvent;
import com.apchavez.customers.domain.event.CustomerEventType;
import com.apchavez.customers.domain.model.Customer;
import com.apchavez.customers.domain.model.CustomerState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaCustomerEventPublisherTest {

    @Mock
    private ReactiveKafkaProducerTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private KafkaCustomerEventPublisher publisher;

    private static final Customer CUSTOMER = new Customer(1, "Alex", "Prieto", CustomerState.ACTIVE, 30);

    @BeforeEach
    void setUp() {
        publisher = new KafkaCustomerEventPublisher(kafkaTemplate, objectMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_shouldSendJsonToKafkaTopic() throws JsonProcessingException {
        CustomerEvent event = CustomerEvent.of(CustomerEventType.CUSTOMER_CREATED, CUSTOMER);
        SenderResult<Void> senderResult = mock(SenderResult.class);

        when(objectMapper.writeValueAsString(event)).thenReturn("{\"eventType\":\"CUSTOMER_CREATED\"}");
        when(kafkaTemplate.send(eq("customer-events"), eq("1"), any()))
                .thenReturn(Mono.just(senderResult));

        StepVerifier.create(publisher.publish(event))
                .verifyComplete();

        verify(kafkaTemplate).send(eq("customer-events"), eq("1"), any());
    }

    @Test
    void publish_shouldCompleteGracefully_whenKafkaFails() throws JsonProcessingException {
        CustomerEvent event = CustomerEvent.of(CustomerEventType.CUSTOMER_CREATED, CUSTOMER);

        when(objectMapper.writeValueAsString(event)).thenReturn("{}");
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("Kafka unavailable")));

        StepVerifier.create(publisher.publish(event))
                .verifyComplete();
    }

    @Test
    void publish_shouldCompleteGracefully_whenSerializationFails() throws JsonProcessingException {
        CustomerEvent event = CustomerEvent.of(CustomerEventType.CUSTOMER_UPDATED, CUSTOMER);

        when(objectMapper.writeValueAsString(event))
                .thenThrow(new JsonProcessingException("serialization error") {});

        StepVerifier.create(publisher.publish(event))
                .verifyComplete();

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}
