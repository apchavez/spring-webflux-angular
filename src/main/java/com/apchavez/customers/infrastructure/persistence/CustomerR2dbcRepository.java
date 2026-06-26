package com.apchavez.customers.infrastructure.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface CustomerR2dbcRepository extends ReactiveCrudRepository<CustomerEntity, Integer> {
    Flux<CustomerEntity> findAllByEstado(String estado);
}
