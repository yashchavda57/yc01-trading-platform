package com.chavd.yc01.apigateway.config;


import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service-auth", r -> r
                        .path("/api/v1/auth/**")
                        .uri("http://localhost:8081"))
                .route("user-service-users", r -> r
                        .path("/api/v1/users/**")
                        .uri("http://localhost:8081"))
                .build();
    }

}