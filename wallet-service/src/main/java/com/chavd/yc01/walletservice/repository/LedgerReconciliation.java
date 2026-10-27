package com.chavd.yc01.walletservice.repository;

import com.chavd.yc01.walletservice.entity.LedgerEntry;
import com.chavd.yc01.walletservice.entity.LedgerEntryType;

import java.math.BigDecimal;
import java.util.List;

/** Pure arithmetic, deliberately separated from the repository so the zero-sum invariant is unit-testable without a database. */
public final class LedgerReconciliation {

    public static BigDecimal net(List<LedgerEntry> entries) {
        return entries.stream()
                .map(e -> e.getEntryType() == LedgerEntryType.DEBIT ? e.getAmount().negate() : e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LedgerReconciliation() {
    }
}
