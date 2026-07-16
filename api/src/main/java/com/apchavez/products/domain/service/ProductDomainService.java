package com.apchavez.products.domain.service;

import com.apchavez.products.domain.exception.DuplicateSkuException;
import com.apchavez.products.domain.exception.ProductNotFoundException;
import com.apchavez.products.domain.model.Product;
import com.apchavez.products.domain.port.ProductRepositoryPort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ProductDomainService {

    private final ProductRepositoryPort repositoryPort;

    public ProductDomainService(ProductRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    public Mono<Product> createProduct(Product product) {
        return repositoryPort.findBySku(product.sku())
                .flatMap(existing -> Mono.<Product>error(new DuplicateSkuException(product.sku())))
                .switchIfEmpty(Mono.defer(() -> repositoryPort.save(product)));
    }

    public Mono<Product> findById(Integer id) {
        return repositoryPort.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)));
    }

    public Mono<Product> findBySku(String sku) {
        return repositoryPort.findBySku(sku);
    }

    public Flux<Product> listActiveProducts(int page, int size) {
        return repositoryPort.findAllActive(page, size);
    }

    public Mono<Long> countActiveProducts() {
        return repositoryPort.countActive();
    }

    public Flux<Product> listInactiveProducts(int page, int size) {
        return repositoryPort.findAllInactive(page, size);
    }

    public Mono<Long> countInactiveProducts() {
        return repositoryPort.countInactive();
    }

    public Flux<Product> searchByNamePrefix(String prefix, int page, int size) {
        return repositoryPort.searchByNamePrefix(prefix, page, size);
    }

    public Mono<Long> countByNamePrefix(String prefix) {
        return repositoryPort.countByNamePrefix(prefix);
    }

    public Flux<Product> findAllProducts() {
        return repositoryPort.findAll();
    }

    public Mono<Product> updateProduct(Integer id, Product updatedData) {
        return repositoryPort.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .flatMap(existing -> repositoryPort.update(
                        new Product(id, updatedData.sku(), updatedData.name(), updatedData.description(),
                                updatedData.category(), updatedData.price(), updatedData.stock(),
                                updatedData.active())));
    }

    public Mono<Product> deleteProduct(Integer id) {
        return repositoryPort.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .flatMap(existing -> repositoryPort.delete(id).thenReturn(existing));
    }
}
