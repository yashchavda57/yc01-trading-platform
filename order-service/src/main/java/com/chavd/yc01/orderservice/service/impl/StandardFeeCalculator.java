package com.chavd.yc01.orderservice.service.impl;

import com.chavd.yc01.orderservice.service.FeeCalculator;

import java.math.BigDecimal;

public class StandardFeeCalculator implements FeeCalculator {
    @Override
    public BigDecimal calculate(BigDecimal tradeValue){
        return tradeValue.multiply(BigDecimal.valueOf(0.001));
    }
}
