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
 * Double-entry bookkeeping: for a given referenceId (e.g. a tradeId), the
 * DEBIT entry on the buyer's wallet and the CREDIT entry on the seller's
 * wallet must be for the same amount -- that's the zero-sum invariant. This
 * table is an append-only fact log (never updated in place), which is what
 * makes reconciliation ("sum of all entries for this reference should net to
 * zero") a simple, always-available query rather than something that can be
 * silently invalidated by an in-place balance update elsewhere.
 */
@Entity
@Table(name = "ledger_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private LedgerEntryType entryType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "reference_type", nullable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
