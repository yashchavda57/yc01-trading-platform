package com.chavd.yc01.portfolioservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure arithmetic, deliberately separated from PortfolioWriteService so the
 * weighted-average-cost and realized-P&L formulas are unit-testable without
 * a database -- same reasoning as wallet-service's LedgerReconciliation.
 */
public final class HoldingMath {

    private static final int SCALE = 8;

    /**
     * Weighted-average cost basis after a BUY fill: the existing position's
     * total cost plus the new fill's cost, spread back over the new total
     * quantity. Scaled to 8 decimal places so repeated small fills don't
     * accumulate visible rounding drift the way 2-4 decimal places would.
     */
    public static BigDecimal newAvgCostBasis(long existingQuantity, BigDecimal existingAvgCostBasis,
                                              long fillQuantity, BigDecimal fillPrice) {
        long newQuantity = existingQuantity + fillQuantity;
        BigDecimal existingCost = existingAvgCostBasis.multiply(BigDecimal.valueOf(existingQuantity));
        BigDecimal fillCost = fillPrice.multiply(BigDecimal.valueOf(fillQuantity));
        return existingCost.add(fillCost).divide(BigDecimal.valueOf(newQuantity), SCALE, RoundingMode.HALF_UP);
    }

    /** Realized P&L for a SELL fill against the position's avg cost basis at the moment of sale. Can be negative. */
    public static BigDecimal realizedPnl(BigDecimal sellPrice, BigDecimal avgCostBasisAtSale, long sellQuantity) {
        return sellPrice.subtract(avgCostBasisAtSale).multiply(BigDecimal.valueOf(sellQuantity));
    }

    private HoldingMath() {
    }
}
