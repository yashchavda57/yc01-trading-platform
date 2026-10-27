package com.chavd.yc01.walletservice.repository;

import com.chavd.yc01.walletservice.entity.FundHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FundHoldRepository extends JpaRepository<FundHold, UUID> {

    Optional<FundHold> findByOrderId(UUID orderId);
}
