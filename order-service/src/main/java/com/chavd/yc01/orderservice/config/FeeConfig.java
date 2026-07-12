package com.chavd.yc01.orderservice.config;

import com.chavd.yc01.orderservice.service.FeeCalculator;
import com.chavd.yc01.orderservice.service.impl.PremiumFeeCalculator;
import com.chavd.yc01.orderservice.service.impl.StandardFeeCalculator;
import com.chavd.yc01.orderservice.service.impl.VipFeeCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeeConfig {

    @Value("${app.account-type}")
    private String accountType;

    @Bean
    public FeeCalculator feeCalculator() {
        return switch (accountType.toUpperCase()) {
            case "VIP" -> new VipFeeCalculator();
            case "PREMIUM" -> new PremiumFeeCalculator();
            case "STANDARD" -> new StandardFeeCalculator();
            default -> throw new IllegalArgumentException("Unknown Account Type: " + accountType);
        };
    }

}
