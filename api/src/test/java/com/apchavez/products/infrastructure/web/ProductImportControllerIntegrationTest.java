package com.apchavez.products.infrastructure.web;

import java.util.Set;
import com.apchavez.products.AbstractIntegrationTest;
import com.apchavez.products.infrastructure.config.JwtService;
import com.apchavez.products.infrastructure.persistence.ProductEntity;
import com.apchavez.products.infrastructure.persistence.ProductR2dbcRepository;
import com.apchavez.products.infrastructure.web.dto.ImportJobResponseDTO;
import com.apchavez.products.infrastructure.web.dto.ImportJobStatusResponseDTO;
import com.apchavez.products.infrastructure.web.dto.ImportRowErrorDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
class ProductImportControllerIntegrationTest extends AbstractIntegrationTest {

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
    void setUp() {
        r2dbcRepository.deleteAll().block();
        r2dbcRepository.save(new ProductEntity(
                null, "SKU-DUP-001", "Existing Product", "desc", "Electronics", 5.0, 1, true)).block();
        adminToken = jwtService.generateToken("test-admin", Set.of("ADMIN"));
        userToken = jwtService.generateToken("test-user", Set.of("USER"));
    }

    @Test
    void importProducts_shouldReturn202_andEventuallyCompleteWithPartialSuccess() {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ClassPathResource("import/sample-products.csv"));

        ImportJobResponseDTO response = webTestClient.post()
                .uri("/api/v1/products/import")
                .header("Authorization", "Bearer " + adminToken)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody(ImportJobResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.jobExecutionId()).isNotNull();

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(300)).untilAsserted(() ->
                webTestClient.get()
                        .uri("/api/v1/products/import/{id}", response.jobExecutionId())
                        .header("Authorization", "Bearer " + userToken)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(ImportJobStatusResponseDTO.class)
                        .value(status -> assertThat(status.status()).isEqualTo("COMPLETED")));

        webTestClient.get()
                .uri("/api/v1/products/import/{id}", response.jobExecutionId())
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ImportJobStatusResponseDTO.class)
                .value(status -> {
                    assertThat(status.readCount()).isEqualTo(7);
                    assertThat(status.writeCount()).isEqualTo(5);
                    assertThat(status.skipCount()).isEqualTo(3);
                    // Content-based, not hasSize(3): ProductImportSkipListener's row-error map is
                    // keyed by the numeric JobExecution ID, which Testcontainers-backed test classes
                    // can coincidentally share across their own independent Postgres databases within
                    // the same JVM test run (each starts its own auto-increment sequence at 1) — a
                    // real single-database production instance never has this collision.
                    assertThat(status.errors())
                            .extracting(ImportRowErrorDTO::sku, ImportRowErrorDTO::message)
                            .contains(
                                    tuple("SKU-DUP-001", "Ya existe un producto con el SKU: SKU-DUP-001"),
                                    tuple("SKU-IMP-007", "El nombre no puede estar vacío"));
                    assertThat(status.errors())
                            .anySatisfy(e -> assertThat(e.message()).contains("Parsing error at line: 8"));
                });
    }

    @Test
    void importProducts_shouldReturn400_whenFileIsNotCsv() {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", "not,a,csv".getBytes()).filename("sample.txt");

        webTestClient.post()
                .uri("/api/v1/products/import")
                .header("Authorization", "Bearer " + adminToken)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void importProducts_shouldReturn401_whenNoToken() {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ClassPathResource("import/sample-products.csv"));

        webTestClient.post()
                .uri("/api/v1/products/import")
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void importProducts_shouldReturn403_whenUserRole() {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ClassPathResource("import/sample-products.csv"));

        webTestClient.post()
                .uri("/api/v1/products/import")
                .header("Authorization", "Bearer " + userToken)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getImportStatus_shouldReturn404_whenNotFound() {
        webTestClient.get()
                .uri("/api/v1/products/import/{id}", 999999)
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus().isNotFound();
    }
}
