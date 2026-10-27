package com.chavd.yc01.walletservice.repository;

import com.chavd.yc01.walletservice.entity.LedgerEntry;
import com.chavd.yc01.walletservice.entity.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByReferenceId(UUID referenceId);

    boolean existsByReferenceIdAndWalletIdAndEntryType(UUID referenceId, UUID walletId, LedgerEntryType entryType);

    /**
     * The reconciliation query named in the Step 17 concept notes: for one
     * reference (e.g. a trade), credits minus debits across every wallet
     * touched by it must net to zero. A nonzero result here means money was
     * created or destroyed by a bug -- instantly detectable, rather than
     * hidden inside a single running balance column. The arithmetic itself is
     * a static method (LedgerReconciliation.net) precisely so it can be unit
     * tested without a database.
     */
    default BigDecimal netForReference(UUID referenceId) {
        return LedgerReconciliation.net(findByReferenceId(referenceId));
    }
}
