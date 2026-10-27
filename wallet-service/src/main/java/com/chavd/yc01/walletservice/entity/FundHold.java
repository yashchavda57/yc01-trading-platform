package com.chavd.yc01.walletservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per order that has ever frozen funds. ACTIVE means still holding;
 * RELEASED means the order was cancelled/rejected and the amount went back to
 * available; SETTLED means a trade converted the hold into an actual ledger
 * debit. The order_id unique index is what makes freezing idempotent against
 * a redelivered order.placed.
 */
@Entity
@Table(name = "fund_holds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundHold {

    @Id
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FundHoldStatus status;

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
