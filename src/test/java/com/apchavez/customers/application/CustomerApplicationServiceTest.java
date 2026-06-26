package com.apchavez.customers.application;

import com.apchavez.customers.domain.exception.ClienteDuplicadoException;
import com.apchavez.customers.domain.exception.ClienteNoEncontradoException;
import com.apchavez.customers.domain.model.Customer;
import com.apchavez.customers.domain.model.CustomerState;
import com.apchavez.customers.domain.port.CustomerRepositoryPort;
import com.apchavez.customers.domain.service.CustomerDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerApplicationServiceTest {

    @Mock
    private CustomerRepositoryPort repositoryPort;

    private CustomerApplicationService applicationService;

    private static final Customer CUSTOMER_WITH_ID =
            new Customer(1, "Alex", "Prieto", CustomerState.ACTIVE, 30);
    private static final Customer CUSTOMER_WITHOUT_ID =
            new Customer(null, "Alex", "Prieto", CustomerState.ACTIVE, 30);
    private static final Customer SAVED_CUSTOMER =
            new Customer(1, "Alex", "Prieto", CustomerState.ACTIVE, 30);

    @BeforeEach
    void setUp() {
        CustomerDomainService domainService = new CustomerDomainService(repositoryPort);
        applicationService = new CustomerApplicationService(domainService);
    }

    // ── createCustomer ───────────────────────────────────────────────────────

    @Test
    void createCustomer_shouldSaveDirectly_whenIdIsNull() {
        when(repositoryPort.save(any())).thenReturn(Mono.just(SAVED_CUSTOMER));

        StepVerifier.create(applicationService.createCustomer(CUSTOMER_WITHOUT_ID))
                .expectNext(SAVED_CUSTOMER)
                .verifyComplete();

        verify(repositoryPort).save(CUSTOMER_WITHOUT_ID);
        verify(repositoryPort, never()).findById(any());
    }

    @Test
    void createCustomer_shouldThrowClienteDuplicadoException_whenIdExists() {
        when(repositoryPort.findById(1)).thenReturn(Mono.just(CUSTOMER_WITH_ID));

        StepVerifier.create(applicationService.createCustomer(CUSTOMER_WITH_ID))
                .expectErrorMatches(e -> e instanceof ClienteDuplicadoException
                        && e.getMessage().contains("1"))
                .verify();

        verify(repositoryPort).findById(1);
        verify(repositoryPort, never()).save(any());
    }

    @Test
    void createCustomer_shouldSave_whenIdNotExistsInRepository() {
        when(repositoryPort.findById(1)).thenReturn(Mono.empty());
        when(repositoryPort.save(any())).thenReturn(Mono.just(SAVED_CUSTOMER));

        StepVerifier.create(applicationService.createCustomer(CUSTOMER_WITH_ID))
                .expectNext(SAVED_CUSTOMER)
                .verifyComplete();

        verify(repositoryPort).findById(1);
        verify(repositoryPort).save(CUSTOMER_WITH_ID);
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_shouldReturnCustomer_whenExists() {
        when(repositoryPort.findById(1)).thenReturn(Mono.just(CUSTOMER_WITH_ID));

        StepVerifier.create(applicationService.findById(1))
                .expectNext(CUSTOMER_WITH_ID)
                .verifyComplete();
    }

    @Test
    void findById_shouldThrowClienteNoEncontradoException_whenNotExists() {
        when(repositoryPort.findById(99)).thenReturn(Mono.empty());

        StepVerifier.create(applicationService.findById(99))
                .expectErrorMatches(e -> e instanceof ClienteNoEncontradoException
                        && e.getMessage().contains("99"))
                .verify();
    }

    // ── listActiveCustomers ──────────────────────────────────────────────────

    @Test
    void listActiveCustomers_shouldDelegateToPort() {
        Customer active1 = new Customer(1, "Carlos", "Lopez", CustomerState.ACTIVE, 22);
        Customer active2 = new Customer(3, "Ana", "Diaz", CustomerState.ACTIVE, 30);
        when(repositoryPort.findAllActive()).thenReturn(Flux.just(active1, active2));

        StepVerifier.create(applicationService.listActiveCustomers())
                .expectNext(active1)
                .expectNext(active2)
                .verifyComplete();

        verify(repositoryPort).findAllActive();
    }
}
