package com.apchavez.customers.infrastructure.web;

import com.apchavez.customers.infrastructure.persistence.CustomerEntity;
import com.apchavez.customers.infrastructure.persistence.CustomerR2dbcRepository;
import com.apchavez.customers.infrastructure.web.dto.CustomerRequestDTO;
import com.apchavez.customers.infrastructure.web.dto.CustomerResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@ActiveProfiles("dev")
class CustomerControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CustomerR2dbcRepository r2dbcRepository;

    @BeforeEach
    void clearDatabase() {
        r2dbcRepository.deleteAll().block();
    }

    // ── POST /api/v1/customers ───────────────────────────────────────────────

    @Test
    void createCustomer_shouldReturn201_withGeneratedId_whenIdIsNull() {
        CustomerRequestDTO request = new CustomerRequestDTO(null, "Alex", "Prieto", "ACTIVE", 30);

        webTestClient.post()
                .uri("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CustomerResponseDTO.class)
                .value(response -> {
                    assertThat(response.id()).isNotNull();
                    assertThat(response.nombre()).isEqualTo("Alex");
                    assertThat(response.apellido()).isEqualTo("Prieto");
                    assertThat(response.estado()).isEqualTo("ACTIVE");
                    assertThat(response.edad()).isEqualTo(30);
                });
    }

    @Test
    void createCustomer_shouldReturn409_whenIdAlreadyExists() {
        // save with null ID so H2 auto-generates it, then reuse that ID
        CustomerEntity existing = r2dbcRepository
                .save(new CustomerEntity(null, "Existing", "User", "ACTIVE", 25))
                .block();

        CustomerRequestDTO request = new CustomerRequestDTO(
                existing.getId(), "Alex", "Prieto", "ACTIVE", 30);

        webTestClient.post()
                .uri("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.mensaje").isNotEmpty();
    }

    @Test
    void createCustomer_shouldReturn400_whenRequestIsInvalid() {
        CustomerRequestDTO request = new CustomerRequestDTO(null, "", null, "INVALID", -1);

        webTestClient.post()
                .uri("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.errores").isArray();
    }

    // ── GET /api/v1/customers/active ─────────────────────────────────────────

    @Test
    void listActiveCustomers_shouldReturn200_withOnlyActiveCustomers() {
        r2dbcRepository.save(new CustomerEntity(null, "Carlos", "Lopez", "ACTIVE", 22)).block();
        r2dbcRepository.save(new CustomerEntity(null, "Maria", "Gomez", "INACTIVE", 20)).block();
        r2dbcRepository.save(new CustomerEntity(null, "Ana", "Diaz", "ACTIVE", 30)).block();

        webTestClient.get()
                .uri("/api/v1/customers/active")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(CustomerResponseDTO.class)
                .value(list -> {
                    assertThat(list).hasSize(2);
                    assertThat(list).allMatch(c -> "ACTIVE".equals(c.estado()));
                });
    }

    @Test
    void listActiveCustomers_shouldReturn200_withEmptyArray_whenNoActiveCustomers() {
        r2dbcRepository.save(new CustomerEntity(null, "Maria", "Gomez", "INACTIVE", 20)).block();

        webTestClient.get()
                .uri("/api/v1/customers/active")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(CustomerResponseDTO.class)
                .hasSize(0);
    }

    // ── GET /api/v1/customers/{id} ───────────────────────────────────────────

    @Test
    void findById_shouldReturn200_whenCustomerExists() {
        CustomerEntity saved = r2dbcRepository
                .save(new CustomerEntity(null, "Alex", "Prieto", "ACTIVE", 30))
                .block();

        webTestClient.get()
                .uri("/api/v1/customers/{id}", saved.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(CustomerResponseDTO.class)
                .value(response -> {
                    assertThat(response.id()).isEqualTo(saved.getId());
                    assertThat(response.nombre()).isEqualTo("Alex");
                });
    }

    @Test
    void findById_shouldReturn404_whenCustomerNotFound() {
        webTestClient.get()
                .uri("/api/v1/customers/{id}", 9999)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.mensaje").isNotEmpty();
    }
}
