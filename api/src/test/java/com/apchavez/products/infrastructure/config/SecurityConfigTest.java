package com.apchavez.products.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    // ── CORS: no allowed-origins configured → no CORS config applied ────────

    @Test
    void corsConfigurationSource_returnsNullConfig_whenNoOriginsConfigured() {
        ReflectionTestUtils.setField(securityConfig, "corsAllowedOrigins", "");

        CorsConfigurationSource source =
                (CorsConfigurationSource) ReflectionTestUtils.invokeMethod(securityConfig, "corsConfigurationSource");
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/products").build());

        CorsConfiguration config = source.getCorsConfiguration(exchange);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).isNull();
    }

    // ── CORS: allowed-origins configured → origins/methods/headers applied ──

    @Test
    void corsConfigurationSource_appliesTrimmedOrigins_whenOriginsConfigured() {
        ReflectionTestUtils.setField(
                securityConfig, "corsAllowedOrigins", "https://example.com, https://foo.bar ,, ");

        CorsConfigurationSource source =
                (CorsConfigurationSource) ReflectionTestUtils.invokeMethod(securityConfig, "corsConfigurationSource");
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/products").build());

        CorsConfiguration config = source.getCorsConfiguration(exchange);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).containsExactly("https://example.com", "https://foo.bar");
        assertThat(config.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(config.getAllowedHeaders()).containsExactly("*");
    }

    // ── JWT authorities: no "roles" claim → no authorities granted ───────────

    @Test
    void jwtAuthenticationConverter_grantsNoAuthorities_whenRolesClaimMissing() {
        ReactiveJwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(claims -> claims.putAll(Map.of("sub", "test-user")))
                .build();

        StepVerifier.create(converter.convert(jwt))
                .assertNext(auth -> assertThat(auth.getAuthorities()).isEmpty())
                .verifyComplete();
    }

    // ── JWT authorities: "roles" claim present → authorities mapped ─────────

    @Test
    void jwtAuthenticationConverter_grantsAuthorities_whenRolesClaimPresent() {
        ReactiveJwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("test-admin")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("roles", List.of("ROLE_ADMIN"))
                .build();

        StepVerifier.create(converter.convert(jwt))
                .assertNext(auth -> assertThat(auth.getAuthorities())
                        .extracting(GrantedAuthority::getAuthority)
                        .containsExactly("ROLE_ADMIN"))
                .verifyComplete();
    }
}
