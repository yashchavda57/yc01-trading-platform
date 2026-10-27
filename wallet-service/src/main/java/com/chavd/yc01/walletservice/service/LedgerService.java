package com.chavd.yc01.walletservice.service;

import com.chavd.yc01.walletservice.entity.LedgerEntry;
import com.chavd.yc01.walletservice.entity.LedgerEntryType;
import com.chavd.yc01.walletservice.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Writes the append-only ledger. The DB unique index on
 * (reference_id, wallet_id, entry_type) (see V3's migration comment) is the
 * real guarantee against a double-debit/credit, but every call here checks
 * first rather than relying on catching the constraint violation: catching it
 * mid-transaction risks Hibernate marking the whole surrounding transaction
 * rollback-only after a flush failure, which would corrupt the wallet-balance
 * update happening in the same @Transactional method. Checking first keeps
 * the common case (an expected replay of an already-processed event) from
 * touching exception handling at all; a genuine concurrent race is rare
 * enough that letting it throw and retry on Kafka redelivery is acceptable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public boolean alreadyRecorded(UUID walletId, LedgerEntryType entryType, UUID referenceId) {
        return ledgerEntryRepository.existsByReferenceIdAndWalletIdAndEntryType(referenceId, walletId, entryType);
    }

    /** @return true if the entry was newly recorded, false if it was already recorded (idempotent replay). */
    public boolean recordEntry(UUID walletId, LedgerEntryType entryType, BigDecimal amount,
                                String referenceType, UUID referenceId, String description) {
        if (alreadyRecorded(walletId, entryType, referenceId)) {
            log.info("Ledger entry already recorded for reference={} wallet={} type={}, skipping (idempotent replay)",
                    referenceId, walletId, entryType);
            return false;
        }
        LedgerEntry entry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .walletId(walletId)
                .entryType(entryType)
                .amount(amount)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .description(description)
                .build();
        ledgerEntryRepository.save(entry);
        return true;
    }
}
