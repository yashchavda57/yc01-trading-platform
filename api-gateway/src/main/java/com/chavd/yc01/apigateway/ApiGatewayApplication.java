package com.chavd.yc01.apigateway;

import com.chavd.yc01.apigateway.properties.GatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class ApiGatewayApplication {
    public static void main (String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
