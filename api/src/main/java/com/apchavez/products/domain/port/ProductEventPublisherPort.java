package com.apchavez.products.domain.port;

import com.apchavez.products.domain.event.ProductEvent;
import reactor.core.publisher.Mono;

public interface ProductEventPublisherPort {
    Mono<Void> publish(ProductEvent event);
}
