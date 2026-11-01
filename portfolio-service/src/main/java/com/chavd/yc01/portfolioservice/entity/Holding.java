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
 * One row per (userId, symbol) the user has ever held. avgCostBasis is the
 * weighted-average price paid per share across every BUY fill still "in" the
 * position -- a SELL reduces quantity but never changes avgCostBasis, which
 * is what lets a later sell at a different price book a correct realized
 * P&L (see RealizedPnlEntry) without needing to know which specific earlier
 * buy lot the shares came from (FIFO/LIFO lot tracking is out of scope here;
 * average cost is the simpler, commonly-used alternative).
 */
@Entity
@Table(name = "holdings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Holding {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "avg_cost_basis", nullable = false)
    private BigDecimal avgCostBasis;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
