package com.apchavez.customers.infrastructure.messaging;

import com.apchavez.customers.domain.event.CustomerEvent;
import com.apchavez.customers.domain.port.CustomerEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnMissingBean(CustomerEventPublisherPort.class)
public class NoOpCustomerEventPublisher implements CustomerEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpCustomerEventPublisher.class);

    @Override
    public Mono<Void> publish(CustomerEvent event) {
        log.debug("Kafka not configured — skipping event: type={}, customerId={}",
                event.eventType(), event.customer().id());
        return Mono.empty();
    }
}
