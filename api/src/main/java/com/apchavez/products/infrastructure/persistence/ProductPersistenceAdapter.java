package com.apchavez.products.infrastructure.persistence;

import com.apchavez.products.domain.exception.ProductNotFoundException;
import com.apchavez.products.domain.model.Product;
import com.apchavez.products.domain.port.ProductRepositoryPort;
import com.apchavez.products.infrastructure.mapper.ProductMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
public class ProductPersistenceAdapter implements ProductRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(ProductPersistenceAdapter.class);

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String PRODUCT_CACHE_PREFIX = "product-cache:";
    private static final String ACTIVE_CACHE_PREFIX = "products-active-cache:";
    private static final String SKU_CACHE_PREFIX = "product-sku-cache:";

    // Dedicated instance rather than the autowired Spring MVC/Jackson bean: this is a reactive,
    // WebEnvironment.NONE-compatible app with no guaranteed ObjectMapper bean in every context
    // (e.g. persistence-only test slices), and record (de)serialization needs no Spring codecs.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProductR2dbcRepository r2dbcRepository;
    private final R2dbcEntityTemplate r2dbcTemplate;
    private final ProductMapper mapper;
    private final ReactiveStringRedisTemplate redisTemplate;

    public ProductPersistenceAdapter(ProductR2dbcRepository r2dbcRepository,
                                      R2dbcEntityTemplate r2dbcTemplate,
                                      ProductMapper mapper,
                                      ReactiveStringRedisTemplate redisTemplate) {
        this.r2dbcRepository = r2dbcRepository;
        this.r2dbcTemplate = r2dbcTemplate;
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Product> save(Product product) {
        return r2dbcRepository.save(mapper.toEntity(product))
                .map(mapper::toDomain)
                .flatMap(saved -> invalidateCaches().thenReturn(saved));
    }

    @Override
    public Mono<Product> update(Product product) {
        return r2dbcTemplate.update(mapper.toEntity(product))
                .map(mapper::toDomain)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(product.id())))
                .flatMap(updated -> invalidateCaches().thenReturn(updated));
    }

    @Override
    public Mono<Product> findById(Integer id) {
        String key = PRODUCT_CACHE_PREFIX + id;
        return readCached(key, Product.class)
                .switchIfEmpty(Mono.defer(() -> r2dbcRepository.findById(id)
                        .map(mapper::toDomain)
                        .flatMap(product -> writeCached(key, product).thenReturn(product))));
    }

    @Override
    public Mono<Product> findBySku(String sku) {
        String key = SKU_CACHE_PREFIX + sku;
        return readCached(key, Product.class)
                .switchIfEmpty(Mono.defer(() -> r2dbcRepository.findBySku(sku)
                        .map(mapper::toDomain)
                        .flatMap(product -> writeCached(key, product).thenReturn(product))));
    }

    @Override
    public Flux<Product> findAllActive(int page, int size) {
        String key = ACTIVE_CACHE_PREFIX + page + ":" + size;
        return readCachedList(key)
                .switchIfEmpty(Mono.defer(() -> r2dbcRepository
                        .findAllByActive(Boolean.TRUE, PageRequest.of(page, size))
                        .map(mapper::toDomain)
                        .collectList()
                        .flatMap(list -> writeCached(key, list).thenReturn(list))))
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Long> countActive() {
        return r2dbcRepository.countByActive(Boolean.TRUE);
    }

    // No cache-aside here (unlike findAllActive): this is a low-traffic admin-only view,
    // not worth the added invalidation surface for a rarely-hit read.
    @Override
    public Flux<Product> findAllInactive(int page, int size) {
        return r2dbcRepository.findAllByActive(Boolean.FALSE, PageRequest.of(page, size))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Long> countInactive() {
        return r2dbcRepository.countByActive(Boolean.FALSE);
    }

    @Override
    public Flux<Product> searchByNamePrefix(String prefix, int page, int size) {
        return r2dbcRepository.findByNameStartingWithIgnoreCase(prefix, PageRequest.of(page, size))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Long> countByNamePrefix(String prefix) {
        return r2dbcRepository.countByNameStartingWithIgnoreCase(prefix);
    }

    // No cache-aside: report generation is a rare, admin-facing batch read over the
    // full table, not a hot path worth adding cache-invalidation surface for.
    @Override
    public Flux<Product> findAll() {
        return r2dbcRepository.findAll().map(mapper::toDomain);
    }

    @Override
    public Mono<Void> delete(Integer id) {
        return r2dbcRepository.deleteById(id)
                .then(invalidateCaches());
    }

    // ---- cache helpers — fail-open: any Redis error is logged and treated as a
    // cache miss/no-op so the product API keeps serving from Postgres uninterrupted. ----

    private Mono<Void> invalidateCaches() {
        return Flux.merge(
                        redisTemplate.keys(PRODUCT_CACHE_PREFIX + "*"),
                        redisTemplate.keys(ACTIVE_CACHE_PREFIX + "*"),
                        redisTemplate.keys(SKU_CACHE_PREFIX + "*"))
                .collectList()
                .flatMap(keys -> keys.isEmpty() ? Mono.just(0L) : redisTemplate.delete(Flux.fromIterable(keys)))
                .onErrorResume(ex -> {
                    log.warn("[CACHE] No se pudo invalidar Redis (fail-open): {}", ex.getMessage());
                    return Mono.just(0L);
                })
                .then();
    }

    private <T> Mono<T> readCached(String key, Class<T> type) {
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> deserialize(json, () -> OBJECT_MAPPER.readValue(json, type)))
                .onErrorResume(ex -> {
                    log.warn("[CACHE] Redis no disponible en lectura (fail-open) — key '{}': {}", key, ex.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<List<Product>> readCachedList(String key) {
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> deserialize(json,
                        () -> OBJECT_MAPPER.readValue(json, new TypeReference<List<Product>>() {})))
                .onErrorResume(ex -> {
                    log.warn("[CACHE] Redis no disponible en lectura (fail-open) — key '{}': {}", key, ex.getMessage());
                    return Mono.empty();
                });
    }

    private <T> Mono<T> deserialize(String json, JsonSupplier<T> supplier) {
        try {
            return Mono.just(supplier.get());
        } catch (Exception e) {
            log.warn("[CACHE] No se pudo deserializar el valor cacheado, se ignora: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<Boolean> writeCached(String key, Object value) {
        return Mono.fromCallable(() -> OBJECT_MAPPER.writeValueAsString(value))
                .flatMap(json -> redisTemplate.opsForValue().set(key, json, CACHE_TTL))
                .onErrorResume(ex -> {
                    log.warn("[CACHE] No se pudo escribir en Redis (fail-open) — key '{}': {}", key, ex.getMessage());
                    return Mono.just(false);
                });
    }

    @FunctionalInterface
    private interface JsonSupplier<T> {
        T get() throws Exception;
    }
}
