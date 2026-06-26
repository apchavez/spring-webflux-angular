package com.apchavez.customers.domain.service;

import com.apchavez.customers.domain.exception.ClienteDuplicadoException;
import com.apchavez.customers.domain.exception.ClienteNoEncontradoException;
import com.apchavez.customers.domain.model.Customer;
import com.apchavez.customers.domain.port.CustomerRepositoryPort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CustomerDomainService {

    private final CustomerRepositoryPort repositoryPort;

    public CustomerDomainService(CustomerRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    public Mono<Customer> createCustomer(Customer customer) {
        if (customer.id() == null) {
            return repositoryPort.save(customer);
        }
        return repositoryPort.findById(customer.id())
                .flatMap(existing -> Mono.<Customer>error(new ClienteDuplicadoException(customer.id())))
                .switchIfEmpty(Mono.defer(() -> repositoryPort.save(customer)));
    }

    public Mono<Customer> findById(Integer id) {
        return repositoryPort.findById(id)
                .switchIfEmpty(Mono.error(new ClienteNoEncontradoException(id)));
    }

    public Flux<Customer> listActiveCustomers() {
        return repositoryPort.findAllActive();
    }
}
