package com.chavd.yc01.trading_platform.service.impl;

import com.chavd.yc01.trading_platform.service.FeeCalculator;

import java.math.BigDecimal;

public class VipFeeCalculator implements FeeCalculator {
    @Override
    public BigDecimal calculate(BigDecimal tradeValue){
        return BigDecimal.ZERO;
    }
}
