package com.chavd.yc01.portfolioservice.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HoldingMathTest {

    @Test
    void firstBuyFromZero_avgCostBasisEqualsFillPrice() {
        BigDecimal avg = HoldingMath.newAvgCostBasis(0, BigDecimal.ZERO, 10, new BigDecimal("100.00"));

        assertEquals(0, avg.compareTo(new BigDecimal("100.00000000")));
    }

    @Test
    void secondBuyAtDifferentPrice_blendsWeightedAverage() {
        // 10 @ 100, then 5 @ 120 -> (1000 + 600) / 15 = 106.66666667 (HALF_UP at 8 places)
        BigDecimal avg = HoldingMath.newAvgCostBasis(10, new BigDecimal("100.00"), 5, new BigDecimal("120.00"));

        assertEquals(0, avg.compareTo(new BigDecimal("106.66666667")));
    }

    @Test
    void buyAtSamePrice_avgCostBasisUnchanged() {
        BigDecimal avg = HoldingMath.newAvgCostBasis(10, new BigDecimal("100.00"), 10, new BigDecimal("100.00"));

        assertEquals(0, avg.compareTo(new BigDecimal("100.00000000")));
    }

    @Test
    void sellAboveCostBasis_realizedPnlIsPositive() {
        BigDecimal pnl = HoldingMath.realizedPnl(new BigDecimal("120.00"), new BigDecimal("100.00"), 10);

        assertEquals(0, pnl.compareTo(new BigDecimal("200.00")));
    }

    @Test
    void sellBelowCostBasis_realizedPnlIsNegative() {
        BigDecimal pnl = HoldingMath.realizedPnl(new BigDecimal("90.00"), new BigDecimal("100.00"), 10);

        assertEquals(0, pnl.compareTo(new BigDecimal("-100.00")));
    }

    @Test
    void sellAtCostBasis_realizedPnlIsZero() {
        BigDecimal pnl = HoldingMath.realizedPnl(new BigDecimal("100.00"), new BigDecimal("100.00"), 10);

        assertEquals(0, pnl.compareTo(BigDecimal.ZERO));
    }
}
