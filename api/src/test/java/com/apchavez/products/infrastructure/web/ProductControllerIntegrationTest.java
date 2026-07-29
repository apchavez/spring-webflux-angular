package com.apchavez.products.infrastructure.web;

import java.util.Set;
import com.apchavez.products.AbstractIntegrationTest;
import com.apchavez.products.infrastructure.config.JwtService;
import com.apchavez.products.infrastructure.persistence.ProductEntity;
import com.apchavez.products.infrastructure.persistence.ProductR2dbcRepository;
import com.apchavez.products.infrastructure.web.dto.ProductRequestDTO;
import com.apchavez.products.infrastructure.web.dto.ProductResponseDTO;
import com.apchavez.products.infrastructure.web.dto.ProductUpdateRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
class ProductControllerIntegrationTest extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProductR2dbcRepository r2dbcRepository;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        r2dbcRepository.deleteAll().block();
        adminToken = jwtService.generateToken("test-admin", Set.of("ADMIN"));
        userToken = jwtService.generateToken("test-user", Set.of("USER"));
    }

    // ── POST /api/v1/products ───────────────────────────────────────────────

    @Test
    void createProduct_shouldReturn201_withGeneratedId() {
        ProductRequestDTO request = new ProductRequestDTO("SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true);

        webTestClient.post()
                .uri("/api/v1/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductResponseDTO.class)
                .value(response -> {
                    assertThat(response.id()).isNotNull();
                    assertThat(response.sku()).isEqualTo("SKU-001");
                    assertThat(response.name()).isEqualTo("Wireless Mouse");
                    assertThat(response.price()).isEqualTo(29.99);
                    assertThat(response.active()).isTrue();
                });
    }

    @Test
    void createProduct_shouldReturn409_whenSkuAlreadyExists() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true)).block();
        ProductRequestDTO request = new ProductRequestDTO("SKU-001", "Another Mouse", "desc", "Electronics", 19.99, 10, true);

        webTestClient.post()
                .uri("/api/v1/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409);
    }

    @Test
    void createProduct_shouldReturn400_whenRequestIsInvalid() {
        ProductRequestDTO request = new ProductRequestDTO("", null, "desc", "cat", -1.0, -1, null);

        webTestClient.post()
                .uri("/api/v1/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.errores").isArray();
    }

    @Test
    void createProduct_shouldReturn401_whenNoToken() {
        ProductRequestDTO request = new ProductRequestDTO("SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true);

        webTestClient.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createProduct_shouldReturn403_whenUserRole() {
        ProductRequestDTO request = new ProductRequestDTO("SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true);

        webTestClient.post()
                .uri("/api/v1/products")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── GET /api/v1/products/active ─────────────────────────────────────────

    @Test
    void listActiveProducts_shouldReturn200_withOnlyActiveProducts() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-001", "Mouse", "desc", "Electronics", 29.99, 150, true)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-002", "Keyboard", "desc", "Electronics", 79.99, 0, false)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-003", "Hub", "desc", "Accessories", 24.50, 80, true)).block();

        webTestClient.get()
                .uri("/api/v1/products/active")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].active").isEqualTo(true)
                .jsonPath("$.content[1].active").isEqualTo(true)
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(20)
                .jsonPath("$.totalElements").isEqualTo(2);
    }

    @Test
    void listActiveProducts_shouldReturn200_withEmptyArray_whenNoActiveProducts() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-002", "Keyboard", "desc", "Electronics", 79.99, 0, false)).block();

        webTestClient.get()
                .uri("/api/v1/products/active")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void listActiveProducts_shouldReturn401_whenNoToken() {
        webTestClient.get()
                .uri("/api/v1/products/active")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── GET /api/v1/products/inactive ───────────────────────────────────────

    @Test
    void listInactiveProducts_shouldReturn200_withOnlyInactiveProducts() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-001", "Mouse", "desc", "Electronics", 29.99, 150, true)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-002", "Keyboard", "desc", "Electronics", 79.99, 0, false)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-003", "Hub", "desc", "Accessories", 24.50, 80, false)).block();

        webTestClient.get()
                .uri("/api/v1/products/inactive")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].active").isEqualTo(false)
                .jsonPath("$.content[1].active").isEqualTo(false)
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(20)
                .jsonPath("$.totalElements").isEqualTo(2);
    }

    @Test
    void listInactiveProducts_shouldReturn200_withEmptyArray_whenNoInactiveProducts() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-001", "Mouse", "desc", "Electronics", 29.99, 150, true)).block();

        webTestClient.get()
                .uri("/api/v1/products/inactive")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void listInactiveProducts_shouldReturn401_whenNoToken() {
        webTestClient.get()
                .uri("/api/v1/products/inactive")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── GET /api/v1/products/search ──────────────────────────────────────────

    @Test
    void searchByNamePrefix_shouldReturn200_withMatchingProducts() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-002", "wireless Keyboard", "desc", "Electronics", 79.99, 10, true)).block();
        r2dbcRepository.save(new ProductEntity(null, "SKU-003", "USB Hub", "desc", "Accessories", 24.50, 80, true)).block();

        webTestClient.get()
                .uri("/api/v1/products/search?prefix=wireless")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.totalElements").isEqualTo(2);
    }

    // ── GET /api/v1/products/sku/{sku} ───────────────────────────────────────

    @Test
    void findBySku_shouldReturn200_whenProductExists() {
        r2dbcRepository.save(new ProductEntity(null, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true)).block();

        webTestClient.get()
                .uri("/api/v1/products/sku/{sku}", "SKU-001")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponseDTO.class)
                .value(response -> assertThat(response.sku()).isEqualTo("SKU-001"));
    }

    @Test
    void findBySku_shouldReturn404_whenProductNotFound() {
        webTestClient.get()
                .uri("/api/v1/products/sku/{sku}", "SKU-NOPE")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── GET /api/v1/products/{id} ───────────────────────────────────────────

    @Test
    void findById_shouldReturn200_whenProductExists() {
        ProductEntity saved = r2dbcRepository
                .save(new ProductEntity(null, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true))
                .block();

        webTestClient.get()
                .uri("/api/v1/products/{id}", saved.getId())
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponseDTO.class)
                .value(response -> {
                    assertThat(response.id()).isEqualTo(saved.getId());
                    assertThat(response.name()).isEqualTo("Wireless Mouse");
                });
    }

    @Test
    void findById_shouldReturn404_whenProductNotFound() {
        webTestClient.get()
                .uri("/api/v1/products/{id}", 9999)
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.mensaje").isNotEmpty();
    }

    @Test
    void findById_shouldReturn400_whenIdIsNegative() {
        webTestClient.get()
                .uri("/api/v1/products/{id}", -1)
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void findById_shouldReturn400_whenIdIsZero() {
        webTestClient.get()
                .uri("/api/v1/products/{id}", 0)
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── PUT /api/v1/products/{id} ───────────────────────────────────────────

    @Test
    void updateProduct_shouldReturn200_withUpdatedData_whenExists() {
        ProductEntity saved = r2dbcRepository
                .save(new ProductEntity(null, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true))
                .block();

        ProductUpdateRequestDTO request =
                new ProductUpdateRequestDTO("SKU-001", "Wireless Mouse Pro", "desc2", "Electronics", 34.99, 120, false);

        webTestClient.put()
                .uri("/api/v1/products/{id}", saved.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponseDTO.class)
                .value(response -> {
                    assertThat(response.id()).isEqualTo(saved.getId());
                    assertThat(response.name()).isEqualTo("Wireless Mouse Pro");
                    assertThat(response.price()).isEqualTo(34.99);
                    assertThat(response.active()).isFalse();
                });
    }

    @Test
    void updateProduct_shouldReturn404_whenNotFound() {
        ProductUpdateRequestDTO request =
                new ProductUpdateRequestDTO("SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true);

        webTestClient.put()
                .uri("/api/v1/products/{id}", 9999)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404);
    }

    @Test
    void updateProduct_shouldReturn400_whenRequestIsInvalid() {
        ProductUpdateRequestDTO request =
                new ProductUpdateRequestDTO("", null, "desc", "cat", -1.0, -1, null);

        webTestClient.put()
                .uri("/api/v1/products/{id}", 1)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.errores").isArray();
    }

    // ── DELETE /api/v1/products/{id} ────────────────────────────────────────

    @Test
    void deleteProduct_shouldReturn204_whenExists() {
        ProductEntity saved = r2dbcRepository
                .save(new ProductEntity(null, "SKU-001", "Wireless Mouse", "desc", "Electronics", 29.99, 150, true))
                .block();

        webTestClient.delete()
                .uri("/api/v1/products/{id}", saved.getId())
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NO_CONTENT);

        webTestClient.get()
                .uri("/api/v1/products/{id}", saved.getId())
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteProduct_shouldReturn404_whenNotFound() {
        webTestClient.delete()
                .uri("/api/v1/products/{id}", 9999)
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404);
    }
}
