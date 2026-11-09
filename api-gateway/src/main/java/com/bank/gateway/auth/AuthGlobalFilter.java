package com.bank.gateway.auth;

import com.bank.gateway.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authenticates every {@code /api/**} call (except login) and enforces the
 * coarse customer/bank role split. Downstream services receive the verified
 * identity as {@code X-Auth-*} headers; any client-supplied X-Auth-* header is
 * stripped first so it cannot be spoofed.
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final AuthProperties properties;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public AuthGlobalFilter(AuthProperties properties, JwtService jwtService,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        if (HttpMethod.OPTIONS.equals(method)
                || !path.startsWith("/api/")
                || path.startsWith("/api/auth/")) {
            return chain.filter(exchange);
        }
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        JwtService.Claims claims;
        try {
            claims = jwtService.verify(authorization.substring(7));
        } catch (JwtService.UnauthorizedException e) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, e.getMessage());
        }
        if (!allowed(claims.role(), method, path)) {
            return reject(exchange, HttpStatus.FORBIDDEN,
                    "Role " + claims.role() + " cannot access " + method + " " + path);
        }
        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> {
                    headers.remove("X-Auth-User");
                    headers.remove("X-Auth-Role");
                    headers.remove("X-Auth-Account");
                })
                .header("X-Auth-User", claims.username())
                .header("X-Auth-Role", claims.role() == null ? "UNKNOWN" : claims.role())
                .headers(headers -> {
                    if (claims.accountId() != null && !claims.accountId().isBlank()) {
                        headers.set("X-Auth-Account", claims.accountId());
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * BANK may do everything. CUSTOMER may read everything except ops/queue/pool
     * and may write only through the customer paths enumerated here.
     */
    static boolean allowed(String role, HttpMethod method, String path) {
        if ("BANK".equals(role)) {
            return true;
        }
        if (!"CUSTOMER".equals(role)) {
            return false;
        }
        if (HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method)) {
            return !path.startsWith("/api/ops/")
                    && !path.startsWith("/api/loans/queue")
                    && !path.startsWith("/api/loans/pool");
        }
        if (HttpMethod.POST.equals(method)) {
            return path.equals("/api/accounts")
                    || path.matches("/api/accounts/[^/]+/(deposits|withdrawals)")
                    || path.equals("/api/transfers")
                    || path.equals("/api/loans")
                    || path.matches("/api/loans/[^/]+/repayment")
                    || path.equals("/api/payments")
                    || path.equals("/api/reservations")
                    || path.matches("/api/reservations/[^/]+/capture");
        }
        return false;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"status\":" + status.value() + "}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
