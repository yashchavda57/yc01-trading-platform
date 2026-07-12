package com.chavd.yc01.orderservice.service.impl;

import com.chavd.yc01.orderservice.service.FeeCalculator;

import java.math.BigDecimal;

public class VipFeeCalculator implements FeeCalculator {
    @Override
    public BigDecimal calculate(BigDecimal tradeValue){
        return BigDecimal.ZERO;
    }
}
