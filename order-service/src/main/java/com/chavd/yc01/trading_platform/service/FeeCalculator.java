package com.chavd.yc01.trading_platform.service;

import java.math.BigDecimal;

public interface FeeCalculator {
    BigDecimal calculate(BigDecimal tradeValue);
}
