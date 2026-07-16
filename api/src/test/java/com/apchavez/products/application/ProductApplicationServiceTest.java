package com.apchavez.products.application;

import com.apchavez.products.domain.event.ProductEvent;
import com.apchavez.products.domain.exception.ProductNotFoundException;
import com.apchavez.products.domain.model.Product;
import com.apchavez.products.domain.port.ProductEventPublisherPort;
import com.apchavez.products.domain.port.ProductRepositoryPort;
import com.apchavez.products.domain.service.ProductDomainService;
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
class ProductApplicationServiceTest {

    @Mock
    private ProductRepositoryPort repositoryPort;

    @Mock
    private ProductEventPublisherPort eventPublisher;

    private ProductApplicationService applicationService;

    private static final Product PRODUCT_WITHOUT_ID =
            new Product(null, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true);
    private static final Product SAVED_PRODUCT =
            new Product(1, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true);

    @BeforeEach
    void setUp() {
        ProductDomainService domainService = new ProductDomainService(repositoryPort);
        applicationService = new ProductApplicationService(domainService, eventPublisher);
        lenient().when(eventPublisher.publish(any(ProductEvent.class))).thenReturn(Mono.empty());
        lenient().when(repositoryPort.findBySku(any())).thenReturn(Mono.empty());
    }

    // ── createProduct ───────────────────────────────────────────────────────

    @Test
    void createProduct_shouldSaveAndPublishEvent() {
        when(repositoryPort.save(any())).thenReturn(Mono.just(SAVED_PRODUCT));

        StepVerifier.create(applicationService.createProduct(PRODUCT_WITHOUT_ID))
                .expectNext(SAVED_PRODUCT)
                .verifyComplete();

        verify(repositoryPort).save(PRODUCT_WITHOUT_ID);
        verify(eventPublisher).publish(any(ProductEvent.class));
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_shouldReturnProduct_whenExists() {
        when(repositoryPort.findById(1)).thenReturn(Mono.just(SAVED_PRODUCT));

        StepVerifier.create(applicationService.findById(1))
                .expectNext(SAVED_PRODUCT)
                .verifyComplete();
    }

    @Test
    void findById_shouldThrowProductNotFoundException_whenNotExists() {
        when(repositoryPort.findById(99)).thenReturn(Mono.empty());

        StepVerifier.create(applicationService.findById(99))
                .expectErrorMatches(e -> e instanceof ProductNotFoundException
                        && e.getMessage().contains("99"))
                .verify();
    }

    // ── listActiveProducts ──────────────────────────────────────────────────

    @Test
    void listActiveProducts_shouldDelegateToPort() {
        Product active1 = new Product(1, "SKU-002", "Keyboard", "desc", "Electronics", 79.99, 10, true);
        Product active2 = new Product(3, "SKU-003", "Hub", "desc", "Accessories", 24.50, 80, true);
        when(repositoryPort.findAllActive(0, 10)).thenReturn(Flux.just(active1, active2));

        StepVerifier.create(applicationService.listActiveProducts(0, 10))
                .expectNext(active1)
                .expectNext(active2)
                .verifyComplete();

        verify(repositoryPort).findAllActive(0, 10);
    }

    // ── listInactiveProducts ────────────────────────────────────────────────

    @Test
    void listInactiveProducts_shouldDelegateToPort() {
        Product inactive1 = new Product(2, "SKU-001", "Mouse", "desc", "Electronics", 29.99, 0, false);
        when(repositoryPort.findAllInactive(0, 10)).thenReturn(Flux.just(inactive1));

        StepVerifier.create(applicationService.listInactiveProducts(0, 10))
                .expectNext(inactive1)
                .verifyComplete();

        verify(repositoryPort).findAllInactive(0, 10);
    }

    // ── updateProduct ───────────────────────────────────────────────────────

    @Test
    void updateProduct_shouldReturnUpdatedProductAndPublishEvent() {
        Product updatedData = new Product(null, "SKU-001", "Wireless Mouse Pro", "desc2", "Electronics", 34.99, 120, false);
        Product expectedResult = new Product(1, "SKU-001", "Wireless Mouse Pro", "desc2", "Electronics", 34.99, 120, false);

        when(repositoryPort.findById(1)).thenReturn(Mono.just(SAVED_PRODUCT));
        when(repositoryPort.update(any())).thenReturn(Mono.just(expectedResult));

        StepVerifier.create(applicationService.updateProduct(1, updatedData))
                .expectNextMatches(p -> p.name().equals("Wireless Mouse Pro") && p.id() == 1)
                .verifyComplete();

        verify(repositoryPort).findById(1);
        verify(repositoryPort).update(expectedResult);
        verify(eventPublisher).publish(any(ProductEvent.class));
    }

    @Test
    void updateProduct_shouldThrowProductNotFoundException_whenNotExists() {
        Product updatedData = new Product(null, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true);
        when(repositoryPort.findById(99)).thenReturn(Mono.empty());

        StepVerifier.create(applicationService.updateProduct(99, updatedData))
                .expectErrorMatches(e -> e instanceof ProductNotFoundException
                        && e.getMessage().contains("99"))
                .verify();
    }

    // ── deleteProduct ───────────────────────────────────────────────────────

    @Test
    void deleteProduct_shouldCompleteAndPublishEvent() {
        when(repositoryPort.findById(1)).thenReturn(Mono.just(SAVED_PRODUCT));
        when(repositoryPort.delete(1)).thenReturn(Mono.empty());

        StepVerifier.create(applicationService.deleteProduct(1))
                .verifyComplete();

        verify(repositoryPort).findById(1);
        verify(repositoryPort).delete(1);
        verify(eventPublisher).publish(any(ProductEvent.class));
    }

    @Test
    void deleteProduct_shouldThrowProductNotFoundException_whenNotExists() {
        when(repositoryPort.findById(99)).thenReturn(Mono.empty());

        StepVerifier.create(applicationService.deleteProduct(99))
                .expectErrorMatches(e -> e instanceof ProductNotFoundException
                        && e.getMessage().contains("99"))
                .verify();
    }
}
