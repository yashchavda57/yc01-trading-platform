package com.chavd.yc01.portfolioservice.repository;

import com.chavd.yc01.portfolioservice.entity.RealizedPnlEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface RealizedPnlEntryRepository extends JpaRepository<RealizedPnlEntry, UUID> {

    /** Idempotency check: has this trade's SELL side already been booked? Guards against Kafka redelivery. */
    boolean existsByTradeId(UUID tradeId);

    /**
     * Sum of every realized-P&L fact ever booked for this user, across all
     * symbols. coalesce(..., 0) so a user with no sells yet gets ZERO back
     * instead of a null that the caller would have to special-case.
     */
    @Query("select coalesce(sum(r.realizedPnl), 0) from RealizedPnlEntry r where r.userId = :userId")
    BigDecimal sumRealizedPnlByUserId(@Param("userId") UUID userId);
}
