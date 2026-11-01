package com.chavd.yc01.portfolioservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only fact log, one row per SELL fill, booked the moment the fill is
 * processed rather than derived later by replaying trade history on every
 * read -- same "fact log, not a mutable running total" shape as wallet-
 * service's LedgerEntry. avgCostBasisAtSale is captured at write time (not
 * joined from the current Holding row at read time) so this row stays a
 * correct historical record even after later trades move the holding's
 * avgCostBasis on. The unique index on tradeId is the idempotency guard
 * against Kafka redelivering the same trade.executed message.
 */
@Entity
@Table(name = "realized_pnl_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealizedPnlEntry {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "trade_id", nullable = false)
    private UUID tradeId;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "sell_price", nullable = false)
    private BigDecimal sellPrice;

    @Column(name = "avg_cost_basis_at_sale", nullable = false)
    private BigDecimal avgCostBasisAtSale;

    @Column(name = "realized_pnl", nullable = false)
    private BigDecimal realizedPnl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
