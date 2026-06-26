package com.apchavez.customers.application;

import com.apchavez.customers.domain.model.Customer;
import com.apchavez.customers.domain.service.CustomerDomainService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CustomerApplicationService {

    private final CustomerDomainService domainService;

    public CustomerApplicationService(CustomerDomainService domainService) {
        this.domainService = domainService;
    }

    public Mono<Customer> createCustomer(Customer customer) {
        return domainService.createCustomer(customer);
    }

    public Mono<Customer> findById(Integer id) {
        return domainService.findById(id);
    }

    public Flux<Customer> listActiveCustomers() {
        return domainService.listActiveCustomers();
    }
}
