package com.chavd.yc01.orderservice.service;

import java.math.BigDecimal;

public interface FeeCalculator {
    BigDecimal calculate(BigDecimal tradeValue);
}
