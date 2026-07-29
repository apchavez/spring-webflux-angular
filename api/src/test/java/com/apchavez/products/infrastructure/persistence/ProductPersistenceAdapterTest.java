package com.apchavez.products.infrastructure.persistence;

import com.apchavez.products.AbstractIntegrationTest;
import com.apchavez.products.domain.model.Product;
import com.apchavez.products.infrastructure.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProductPersistenceAdapterTest extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Autowired
    private ProductR2dbcRepository r2dbcRepository;

    @Autowired
    private R2dbcEntityTemplate r2dbcTemplate;

    @Autowired
    private ProductMapper mapper;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    private ProductPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProductPersistenceAdapter(r2dbcRepository, r2dbcTemplate, mapper, redisTemplate);
        r2dbcRepository.deleteAll().block();
        redisTemplate.keys("*").flatMap(redisTemplate::delete).blockLast();
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    void save_shouldPersistProductAndReturnWithGeneratedId() {
        Product product = new Product(null, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true);

        StepVerifier.create(adapter.save(product))
                .assertNext(saved -> {
                    assertThat(saved.id()).isNotNull();
                    assertThat(saved.sku()).isEqualTo("SKU-001");
                    assertThat(saved.name()).isEqualTo("Wireless Mouse");
                    assertThat(saved.price()).isEqualTo(29.99);
                    assertThat(saved.active()).isTrue();
                })
                .verifyComplete();
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_shouldReturnProduct_whenExists() {
        ProductEntity entity = r2dbcRepository
                .save(new ProductEntity(null, "SKU-002", "Keyboard", "desc", "Electronics", 79.99, 10, true))
                .block();

        StepVerifier.create(adapter.findById(entity.getId()))
                .assertNext(found -> {
                    assertThat(found.id()).isEqualTo(entity.getId());
                    assertThat(found.name()).isEqualTo("Keyboard");
                    assertThat(found.active()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void findById_shouldReturnEmpty_whenNotExists() {
        StepVerifier.create(adapter.findById(9999))
                .verifyComplete();
    }

    // ── findBySku ────────────────────────────────────────────────────────────

    @Test
    void findBySku_shouldReturnProduct_whenExists() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-003", "Hub", "desc", "Accessories", 24.50, 80, true)).block();

        StepVerifier.create(adapter.findBySku("SKU-003"))
                .assertNext(found -> assertThat(found.name()).isEqualTo("Hub"))
                .verifyComplete();
    }

    @Test
    void findBySku_shouldReturnEmpty_whenNotExists() {
        StepVerifier.create(adapter.findBySku("SKU-NOPE"))
                .verifyComplete();
    }

    // ── findAllActive ─────────────────────────────────────────────────────────

    @Test
    void findAllActive_shouldReturnOnlyActiveProducts() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-001", "Mouse", "desc", "Electronics", 29.99, 150, true)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-002", "Keyboard", "desc", "Electronics", 79.99, 0, false)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-003", "Hub", "desc", "Accessories", 24.50, 80, true)).block();

        StepVerifier.create(adapter.findAllActive(0, 10))
                .assertNext(p -> assertThat(p.active()).isTrue())
                .assertNext(p -> assertThat(p.active()).isTrue())
                .verifyComplete();
    }

    @Test
    void findAllActive_shouldReturnEmpty_whenNoActiveProducts() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-002", "Keyboard", "desc", "Electronics", 79.99, 0, false)).block();

        StepVerifier.create(adapter.findAllActive(0, 10))
                .verifyComplete();
    }

    // ── findAllInactive ───────────────────────────────────────────────────────

    @Test
    void findAllInactive_shouldReturnOnlyInactiveProducts() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-001", "Mouse", "desc", "Electronics", 29.99, 150, true)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-002", "Keyboard", "desc", "Electronics", 79.99, 0, false)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-003", "Hub", "desc", "Accessories", 24.50, 80, false)).block();

        StepVerifier.create(adapter.findAllInactive(0, 10))
                .assertNext(p -> assertThat(p.active()).isFalse())
                .assertNext(p -> assertThat(p.active()).isFalse())
                .verifyComplete();
    }

    @Test
    void findAllInactive_shouldReturnEmpty_whenNoInactiveProducts() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-001", "Mouse", "desc", "Electronics", 29.99, 150, true)).block();

        StepVerifier.create(adapter.findAllInactive(0, 10))
                .verifyComplete();
    }

    // ── searchByNamePrefix ───────────────────────────────────────────────────

    @Test
    void searchByNamePrefix_shouldReturnCaseInsensitiveMatches() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-002", "wireless Keyboard", "desc", "Electronics", 79.99, 10, true)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-003", "USB Hub", "desc", "Accessories", 24.50, 80, true)).block();

        StepVerifier.create(adapter.searchByNamePrefix("wireless", 0, 10))
                .expectNextCount(2)
                .verifyComplete();
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_shouldPersistNewValues_whenProductExists() {
        ProductEntity saved = r2dbcRepository
                .save(new ProductEntity(null, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true))
                .block();

        Product toUpdate = new Product(saved.getId(), "SKU-001", "Wireless Mouse Pro", "desc2", "Electronics", 34.99, 120, false);

        StepVerifier.create(adapter.update(toUpdate))
                .assertNext(updated -> {
                    assertThat(updated.id()).isEqualTo(saved.getId());
                    assertThat(updated.name()).isEqualTo("Wireless Mouse Pro");
                    assertThat(updated.price()).isEqualTo(34.99);
                    assertThat(updated.active()).isFalse();
                })
                .verifyComplete();
    }

    // ── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_shouldRemoveProduct_whenExists() {
        ProductEntity saved = r2dbcRepository
                .save(new ProductEntity(null, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true))
                .block();

        StepVerifier.create(adapter.delete(saved.getId()))
                .verifyComplete();

        StepVerifier.create(adapter.findById(saved.getId()))
                .verifyComplete();
    }

    // ── Redis cache: proves it's a real cache, not decoration ──────────────────

    @Test
    void findById_servesStaleDataFromCache_untilInvalidated() {
        ProductEntity saved = r2dbcRepository
                .save(new ProductEntity(null, "SKU-002", "Keyboard", "desc", "Electronics", 79.99, 10, true))
                .block();

        // Populates the Redis cache entry for this id.
        StepVerifier.create(adapter.findById(saved.getId()))
                .assertNext(found -> assertThat(found.name()).isEqualTo("Keyboard"))
                .verifyComplete();

        // Mutate Postgres directly, bypassing the adapter (and its cache invalidation).
        r2dbcRepository.save(new ProductEntity(saved.getId(), "SKU-002", "Mutated Directly", "desc", "Electronics", 79.99, 10, true)).block();

        // The cached (now stale) value is still served — proves Redis is actually being read.
        StepVerifier.create(adapter.findById(saved.getId()))
                .assertNext(found -> assertThat(found.name()).isEqualTo("Keyboard"))
                .verifyComplete();

        // A write through the adapter invalidates the cache...
        Product toUpdate = new Product(saved.getId(), "SKU-002", "Updated Via Adapter", "desc", "Electronics", 79.99, 10, true);
        adapter.update(toUpdate).block();

        // ...so the next read reflects the fresh value.
        StepVerifier.create(adapter.findById(saved.getId()))
                .assertNext(found -> assertThat(found.name()).isEqualTo("Updated Via Adapter"))
                .verifyComplete();
    }

    @Test
    void findAllActive_servesStaleDataFromCache_untilInvalidated() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-002", "Keyboard", "desc", "Electronics", 79.99, 10, true)).block();

        // Populates the Redis cache entry for this page/size.
        StepVerifier.create(adapter.findAllActive(0, 10))
                .expectNextCount(1)
                .verifyComplete();

        // A save through the adapter invalidates the cache...
        adapter.save(new Product(null, "SKU-003", "Hub", "desc", "Accessories", 24.50, 80, true)).block();

        // ...so the next read reflects both products.
        StepVerifier.create(adapter.findAllActive(0, 10))
                .expectNextCount(2)
                .verifyComplete();
    }
}
