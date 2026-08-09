package com.apchavez.products.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ProductR2dbcRepository extends ReactiveCrudRepository<ProductEntity, Integer> {
    Flux<ProductEntity> findAllByActive(Boolean active, Pageable pageable);
    Mono<Long> countByActive(Boolean active);
    Mono<ProductEntity> findBySku(String sku);
    Flux<ProductEntity> findByNameStartingWithIgnoreCase(String prefix, Pageable pageable);
    Mono<Long> countByNameStartingWithIgnoreCase(String prefix);
}
