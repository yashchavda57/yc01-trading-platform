package com.chavd.yc01.trading_platform.service.impl;

import com.chavd.yc01.trading_platform.service.FeeCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PremiumFeeCalculator implements FeeCalculator {
    @Override
    public BigDecimal calculate(BigDecimal tradeValue){
        return tradeValue.multiply(BigDecimal.valueOf(0.0005));
    }
}
