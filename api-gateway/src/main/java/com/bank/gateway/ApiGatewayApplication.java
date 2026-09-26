package com.bank.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("account-command-service", r -> r.path("/api/accounts/**")
                        .uri("lb://account-command-service"))
                .route("ledger-query-service", r -> r.path("/api/ledger/**")
                        .uri("lb://ledger-query-service"))
                .route("fraud-detection-service", r -> r.path("/api/fraud/**")
                        .uri("lb://fraud-detection-service"))
                .route("notification-service", r -> r.path("/api/notifications/**")
                        .uri("lb://notification-service"))
                .build();
    }
}