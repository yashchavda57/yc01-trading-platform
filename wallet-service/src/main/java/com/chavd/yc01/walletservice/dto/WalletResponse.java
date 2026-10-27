package com.chavd.yc01.walletservice.dto;

import com.chavd.yc01.walletservice.entity.Wallet;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record WalletResponse(
        UUID userId,
        BigDecimal availableBalance,
        BigDecimal heldBalance
) {
    public static WalletResponse from(Wallet wallet) {
        return WalletResponse.builder()
                .userId(wallet.getUserId())
                .availableBalance(wallet.getAvailableBalance())
                .heldBalance(wallet.getHeldBalance())
                .build();
    }
}
