package com.apchavez.products.domain.port;

import com.apchavez.products.domain.model.Product;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductRepositoryPort {
    Mono<Product> save(Product product);
    Mono<Product> update(Product product);
    Mono<Product> findById(Integer id);
    Mono<Product> findBySku(String sku);
    Flux<Product> findAllActive(int page, int size);
    Mono<Long> countActive();
    Flux<Product> findAllInactive(int page, int size);
    Mono<Long> countInactive();
    Flux<Product> searchByNamePrefix(String prefix, int page, int size);
    Mono<Long> countByNamePrefix(String prefix);
    Flux<Product> findAll();
    Mono<Void> delete(Integer id);
}
