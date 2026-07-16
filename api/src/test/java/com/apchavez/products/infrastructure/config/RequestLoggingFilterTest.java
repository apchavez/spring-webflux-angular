package com.apchavez.products.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    private MockServerWebExchange buildExchange(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, path).build();
        return MockServerWebExchange.from(request);
    }

    // ── Happy path: request id header set, chain completes ──────────────────

    @Test
    void should_addRequestIdHeader_andCompleteSuccessfully_whenChainSucceeds() {
        MockServerWebExchange exchange = buildExchange("/api/v1/products/active");
        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    // ── Error path: chain fails, doOnError logs and the error still propagates ──

    @Test
    void should_propagateError_andLog_whenChainFails() {
        MockServerWebExchange exchange = buildExchange("/api/v1/products");
        RuntimeException boom = new RuntimeException("downstream failure");
        WebFilterChain chain = ex -> Mono.error(boom);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(e -> e == boom)
                .verify();

        // The request id header is still assigned before the chain runs, even on failure.
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }
}
