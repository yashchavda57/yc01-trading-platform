package com.chavd.yc01.apigateway.filter;

import com.chavd.yc01.apigateway.properties.GatewayProperties;
import com.chavd.yc01.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtTokenProvider jwtTokenProvider;
    private final GatewayProperties gatewayProperties;

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (gatewayProperties.getPublicPaths().stream().anyMatch(path::startsWith)){
            return chain.filter(exchange);
        }

        //2. Read authorization header
        String authHeader = exchange.getRequest()
                .getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalide Authorization Header");
        }

        //3. Validate Token
        String token = authHeader.substring(7);
        if (!jwtTokenProvider.isTokenValid(token)){
            return unauthorized(exchange,"Invalid or expired token");
        }

        //4. Extract claims and forward as heaeders
        String email = jwtTokenProvider.extractUsername(token);
        String role = jwtTokenProvider.extractRole(token);

        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header("X-User-Email", email)
                .header("X-User-Role", role)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        String body = "{\"success\":false, \"message\": \"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

}