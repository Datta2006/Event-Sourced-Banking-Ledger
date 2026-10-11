package com.bank.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * API Gateway (v2).
 *
 * Spring Cloud Gateway (reactive) + Eureka discovery entry point. Routes are
 * declared in application.yml: every {@code /api/<service-segment>/**} is
 * load-balanced ({@code lb://service-id}) to the backing microservice and the
 * {@code /api} prefix is stripped, so the browser talks to one origin only.
 * Client {@code Idempotency-Key} headers are forwarded unchanged (default
 * behavior — the gateway never strips unrecognized headers).
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /**
     * The dev frontend runs on Vite (:5173). CORS is centralized here so the 7
     * backing services stay closed to browsers.
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location", "Idempotency-Key"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
