package com.chavd.yc01.userservice.config;

import com.chavd.yc01.common.security.JwtProperties;
import com.chavd.yc01.common.security.JwtTokenProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.jwt")
    public JwtProperties jwtProperties(){
        return new JwtProperties();
    }

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
        return new JwtTokenProvider(jwtProperties);
    }

}
