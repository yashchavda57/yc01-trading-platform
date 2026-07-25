package com.chavd.yc01.apigateway.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "app")
public class GatewayProperties {
    private List<String> publicPaths;
}
