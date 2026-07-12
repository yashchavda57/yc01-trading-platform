package com.chavd.yc01.orderservice.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TradeService {

    private final FeeCalculator feeCalculator;

    public TradeService(FeeCalculator feeCalculator){
        this.feeCalculator = feeCalculator;
    }

    public BigDecimal executeTrade(BigDecimal tradeValue){
        return tradeValue.subtract(feeCalculator.calculate(tradeValue));
    }
}
