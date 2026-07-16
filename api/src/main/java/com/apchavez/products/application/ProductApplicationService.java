package com.apchavez.products.application;

import com.apchavez.products.domain.event.ProductEvent;
import com.apchavez.products.domain.model.Product;
import com.apchavez.products.domain.port.ProductEventPublisherPort;
import com.apchavez.products.domain.service.ProductDomainService;
import com.apchavez.products.infrastructure.config.RequestLoggingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.apchavez.products.domain.event.ProductEventType.*;

@Service
public class ProductApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ProductApplicationService.class);

    private final ProductDomainService domainService;
    private final ProductEventPublisherPort eventPublisher;

    public ProductApplicationService(ProductDomainService domainService,
                                      ProductEventPublisherPort eventPublisher) {
        this.domainService = domainService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Mono<Product> createProduct(Product product) {
        return Mono.deferContextual(ctx -> {
            String rid = ctx.getOrDefault(RequestLoggingFilter.REQUEST_ID_CONTEXT_KEY, "-");
            log.info("[{}] Crear producto — sku='{}', name='{}'",
                    rid, product.sku(), product.name());
            return domainService.createProduct(product)
                    .flatMap(saved -> eventPublisher.publish(ProductEvent.of(PRODUCT_CREATED, saved))
                            .thenReturn(saved))
                    .doOnSuccess(saved -> log.info("[{}] Producto creado — id={}", rid, saved.id()));
        });
    }

    public Mono<Product> findById(Integer id) {
        return Mono.deferContextual(ctx -> {
            log.debug("[{}] Buscar producto — id={}",
                    ctx.getOrDefault(RequestLoggingFilter.REQUEST_ID_CONTEXT_KEY, "-"), id);
            return domainService.findById(id);
        });
    }

    public Mono<Product> findBySku(String sku) {
        log.debug("Buscar producto — sku={}", sku);
        return domainService.findBySku(sku);
    }

    public Flux<Product> listActiveProducts(int page, int size) {
        log.debug("Listar productos activos — página={}, tamaño={}", page, size);
        return domainService.listActiveProducts(page, size);
    }

    public Mono<Long> countActiveProducts() {
        return domainService.countActiveProducts();
    }

    public Flux<Product> listInactiveProducts(int page, int size) {
        log.debug("Listar productos inactivos — página={}, tamaño={}", page, size);
        return domainService.listInactiveProducts(page, size);
    }

    public Mono<Long> countInactiveProducts() {
        return domainService.countInactiveProducts();
    }

    public Flux<Product> searchByNamePrefix(String prefix, int page, int size) {
        log.debug("Buscar productos por prefijo de nombre — prefix={}, página={}, tamaño={}", prefix, page, size);
        return domainService.searchByNamePrefix(prefix, page, size);
    }

    public Mono<Long> countByNamePrefix(String prefix) {
        return domainService.countByNamePrefix(prefix);
    }

    public Flux<Product> findAllProducts() {
        log.debug("Listar todos los productos para reporte");
        return domainService.findAllProducts();
    }

    @Transactional
    public Mono<Product> updateProduct(Integer id, Product updatedData) {
        return Mono.deferContextual(ctx -> {
            String rid = ctx.getOrDefault(RequestLoggingFilter.REQUEST_ID_CONTEXT_KEY, "-");
            log.info("[{}] Actualizar producto — id={}", rid, id);
            return domainService.updateProduct(id, updatedData)
                    .flatMap(updated -> eventPublisher.publish(ProductEvent.of(PRODUCT_UPDATED, updated))
                            .thenReturn(updated))
                    .doOnSuccess(updated -> log.info("[{}] Producto actualizado — id={}", rid, updated.id()));
        });
    }

    @Transactional
    public Mono<Void> deleteProduct(Integer id) {
        return Mono.deferContextual(ctx -> {
            String rid = ctx.getOrDefault(RequestLoggingFilter.REQUEST_ID_CONTEXT_KEY, "-");
            log.info("[{}] Eliminar producto — id={}", rid, id);
            return domainService.deleteProduct(id)
                    .flatMap(deleted -> eventPublisher.publish(ProductEvent.of(PRODUCT_DELETED, deleted)))
                    .doOnSuccess(v -> log.info("[{}] Producto eliminado — id={}", rid, id));
        });
    }
}
