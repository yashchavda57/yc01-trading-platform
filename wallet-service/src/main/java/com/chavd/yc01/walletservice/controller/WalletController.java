package com.chavd.yc01.walletservice.controller;

import com.chavd.yc01.common.dto.ApiResponse;
import com.chavd.yc01.walletservice.dto.DepositRequest;
import com.chavd.yc01.walletservice.dto.WalletResponse;
import com.chavd.yc01.walletservice.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Deposit is a test/manual-funding convenience, not part of the Step 17 spec
 * (which only describes reacting to order/trade events) -- added so freeze()
 * has real available balance to work against during verification. A real
 * deposit flow (bank transfer, card) is out of scope for this project.
 */
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/{userId}")
    public ApiResponse<WalletResponse> getWallet(@PathVariable UUID userId) {
        return ApiResponse.ok(WalletResponse.from(walletService.getOrCreateWallet(userId)));
    }

    @PostMapping("/{userId}/deposit")
    public ApiResponse<WalletResponse> deposit(@PathVariable UUID userId, @Valid @RequestBody DepositRequest request) {
        return ApiResponse.ok(WalletResponse.from(walletService.deposit(userId, request.amount())));
    }
}
