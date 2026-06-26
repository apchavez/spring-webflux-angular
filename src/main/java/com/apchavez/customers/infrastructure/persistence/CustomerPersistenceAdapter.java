package com.apchavez.customers.infrastructure.persistence;

import com.apchavez.customers.domain.model.Customer;
import com.apchavez.customers.domain.port.CustomerRepositoryPort;
import com.apchavez.customers.infrastructure.mapper.CustomerMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CustomerPersistenceAdapter implements CustomerRepositoryPort {

    private final CustomerR2dbcRepository r2dbcRepository;
    private final CustomerMapper mapper;

    public CustomerPersistenceAdapter(CustomerR2dbcRepository r2dbcRepository, CustomerMapper mapper) {
        this.r2dbcRepository = r2dbcRepository;
        this.mapper = mapper;
    }

    @Override
    public Mono<Customer> save(Customer customer) {
        return r2dbcRepository.save(mapper.toEntity(customer))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Customer> findById(Integer id) {
        return r2dbcRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Flux<Customer> findAllActive() {
        return r2dbcRepository.findAllByEstado("ACTIVE")
                .map(mapper::toDomain);
    }
}
