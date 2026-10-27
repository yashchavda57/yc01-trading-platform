package com.chavd.yc01.walletservice.repository;

import com.chavd.yc01.walletservice.entity.LedgerEntry;
import com.chavd.yc01.walletservice.entity.LedgerEntryType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgerReconciliationTest {

    private LedgerEntry entry(LedgerEntryType type, String amount) {
        return LedgerEntry.builder()
                .id(UUID.randomUUID())
                .walletId(UUID.randomUUID())
                .entryType(type)
                .amount(new BigDecimal(amount))
                .referenceType("TRADE")
                .referenceId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void matchingDebitAndCredit_netsToZero() {
        List<LedgerEntry> entries = List.of(
                entry(LedgerEntryType.DEBIT, "100.00"),
                entry(LedgerEntryType.CREDIT, "100.00"));

        assertEquals(0, LedgerReconciliation.net(entries).compareTo(BigDecimal.ZERO));
    }

    @Test
    void mismatchedAmounts_doNotNetToZero() {
        List<LedgerEntry> entries = List.of(
                entry(LedgerEntryType.DEBIT, "100.00"),
                entry(LedgerEntryType.CREDIT, "90.00"));

        assertEquals(0, LedgerReconciliation.net(entries).compareTo(new BigDecimal("-10.00")));
    }

    @Test
    void emptyList_netsToZero() {
        assertEquals(0, LedgerReconciliation.net(List.of()).compareTo(BigDecimal.ZERO));
    }

    @Test
    void onlyADebit_isNegative() {
        List<LedgerEntry> entries = List.of(entry(LedgerEntryType.DEBIT, "50.00"));

        assertEquals(0, LedgerReconciliation.net(entries).compareTo(new BigDecimal("-50.00")));
    }
}
