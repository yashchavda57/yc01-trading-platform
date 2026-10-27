package com.chavd.yc01.walletservice.repository;

import com.chavd.yc01.walletservice.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    /**
     * SELECT ... FOR UPDATE, contrasted with Phase 1's optimistic @Version on
     * User: wallet balance updates are high-contention and a lost/retried
     * optimistic-lock transaction on money movement is worse than briefly
     * blocking a second writer. Every balance mutation in WalletService goes
     * through this method, inside a @Transactional boundary, so concurrent
     * updates to the same user's wallet are serialized at the DB row level
     * rather than detected-and-retried after the fact.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(UUID userId);

    /** Same as above but by wallet id, for callers (e.g. releasing a hold) that only have the wallet id on hand. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(UUID id);
}
