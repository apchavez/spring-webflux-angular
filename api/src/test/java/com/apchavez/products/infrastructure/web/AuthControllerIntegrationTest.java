package com.apchavez.products.infrastructure.web;

import com.apchavez.products.AbstractIntegrationTest;
import com.apchavez.products.infrastructure.web.dto.LoginRequestDTO;
import com.apchavez.products.infrastructure.web.dto.LoginResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void login_shouldReturnToken_whenAdminCredentialsAreCorrect() {
        LoginResponseDTO response = webTestClient.post()
                .uri("/api/v1/auth/login")
                .bodyValue(new LoginRequestDTO("admin", "admin123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
    }

    @Test
    void login_shouldReturn401_whenPasswordIsWrong() {
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .bodyValue(new LoginRequestDTO("admin", "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void login_shouldReturn400_whenFieldsAreBlank() {
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .bodyValue(new LoginRequestDTO("", ""))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void tokenIssuedByLogin_shouldActuallyAuthorizeAProtectedEndpoint() {
        LoginResponseDTO login = webTestClient.post()
                .uri("/api/v1/auth/login")
                .bodyValue(new LoginRequestDTO("user", "user123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(login).isNotNull();

        webTestClient.get()
                .uri("/api/v1/products/active")
                .header("Authorization", "Bearer " + login.token())
                .exchange()
                .expectStatus().isOk();
    }
}
