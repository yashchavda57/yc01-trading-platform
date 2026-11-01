package com.chavd.yc01.portfolioservice.repository;

import com.chavd.yc01.portfolioservice.entity.Holding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HoldingRepository extends JpaRepository<Holding, UUID> {

    /** Plain read for the CQRS read side's cache-miss fallback -- never used on the write path. */
    List<Holding> findByUserId(UUID userId);

    /**
     * SELECT ... FOR UPDATE, same reasoning as WalletRepository.findByUserIdForUpdate:
     * a holding update (new avg cost basis, +/- quantity) is a read-modify-write
     * that must not race with a concurrent fill for the same (userId, symbol), so
     * concurrent writers are serialized at the DB row level instead of relying on
     * optimistic-lock retry.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Holding h where h.userId = :userId and h.symbol = :symbol")
    Optional<Holding> findByUserIdAndSymbolForUpdate(UUID userId, String symbol);
}
