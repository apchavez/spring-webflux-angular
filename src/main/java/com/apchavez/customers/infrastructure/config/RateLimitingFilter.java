package com.apchavez.customers.infrastructure.config;

import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter implements WebFilter {

    private static final int MAX_REQUESTS = 100;
    private static final long WINDOW_MILLIS = 60_000L;
    private static final String TARGET_PATH = "/api/v1/customers";

    private final ConcurrentHashMap<String, AtomicInteger> windowCounts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public RateLimitingFilter() {
        // Elimina entradas de ventanas pasadas cada minuto para evitar memory leak
        scheduler.scheduleAtFixedRate(
                this::purgeOldWindows, WINDOW_MILLIS, WINDOW_MILLIS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void purgeOldWindows() {
        long currentWindow = System.currentTimeMillis() / WINDOW_MILLIS;
        windowCounts.keySet().removeIf(key -> {
            int colonIdx = key.lastIndexOf(':');
            try {
                return Long.parseLong(key.substring(colonIdx + 1)) < currentWindow;
            } catch (NumberFormatException e) {
                return true;
            }
        });
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (!HttpMethod.POST.equals(request.getMethod()) ||
                !TARGET_PATH.equals(request.getPath().value())) {
            return chain.filter(exchange);
        }

        String ip = extractClientIp(request);
        long currentWindow = System.currentTimeMillis() / WINDOW_MILLIS;
        String windowKey = ip + ":" + currentWindow;

        AtomicInteger count = windowCounts.computeIfAbsent(windowKey, k -> new AtomicInteger(0));
        int current = count.incrementAndGet();

        if (current > MAX_REQUESTS) {
            long windowStart = currentWindow * WINDOW_MILLIS;
            long retryAfter = (WINDOW_MILLIS - (System.currentTimeMillis() - windowStart)) / 1000 + 1;
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(retryAfter));
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    private String extractClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
