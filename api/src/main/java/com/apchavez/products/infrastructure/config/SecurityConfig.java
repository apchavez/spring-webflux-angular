package com.apchavez.products.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String API_V1_PATTERN = "/api/v1/**";
    private static final String ADMIN_ROLE = "ADMIN";

    @Value("${security.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .pathMatchers(HttpMethod.GET, API_V1_PATTERN).authenticated()
                        .pathMatchers(HttpMethod.POST, API_V1_PATTERN).hasRole(ADMIN_ROLE)
                        .pathMatchers(HttpMethod.PUT, API_V1_PATTERN).hasRole(ADMIN_ROLE)
                        .pathMatchers(HttpMethod.DELETE, API_V1_PATTERN).hasRole(ADMIN_ROLE)
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .headers(headers -> headers
                        .contentTypeOptions(spec -> {})
                        .frameOptions(spec -> spec.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        .contentSecurityPolicy(spec -> spec.policyDirectives("default-src 'self'"))
                        .referrerPolicy(spec -> spec.policy(
                                ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.NO_REFERRER)))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .build();
    }

    @Bean
    ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) return Flux.empty();
            return Flux.fromIterable(roles).map(SimpleGrantedAuthority::new);
        });
        return converter;
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Stream.of(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        if (!origins.isEmpty()) {
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("*"));
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
