package com.chavd.yc01.walletservice.service;

import com.chavd.yc01.walletservice.entity.FundHold;
import com.chavd.yc01.walletservice.entity.FundHoldStatus;
import com.chavd.yc01.walletservice.entity.LedgerEntryType;
import com.chavd.yc01.walletservice.entity.Wallet;
import com.chavd.yc01.walletservice.repository.FundHoldRepository;
import com.chavd.yc01.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Every balance mutation here runs inside a @Transactional method and reads
 * the wallet row via WalletRepository's PESSIMISTIC_WRITE query first -- see
 * that interface's javadoc for why pessimistic (not optimistic, unlike
 * Phase 1's User) is the right call for money.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final FundHoldRepository fundHoldRepository;
    private final LedgerService ledgerService;

    @Transactional
    public Wallet getOrCreateWallet(UUID userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            try {
                return walletRepository.save(Wallet.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .availableBalance(BigDecimal.ZERO)
                        .heldBalance(BigDecimal.ZERO)
                        .build());
            } catch (DataIntegrityViolationException e) {
                // Lost a creation race to a concurrent request for the same
                // never-before-seen user; the winner's row is what we want.
                return walletRepository.findByUserId(userId).orElseThrow();
            }
        });
    }

    /**
     * Freezes {@code amount} against the order. Does nothing (idempotent) if
     * this order already has a hold. If the wallet doesn't have enough
     * available balance, the hold is skipped and a warning logged -- this
     * service has no way to reject an order that's already been accepted by
     * order-service; a synchronous pre-trade balance check belongs in
     * risk-service (Phase 3, not built yet). Documented as a known limitation.
     */
    @Transactional
    public void freeze(UUID userId, UUID orderId, BigDecimal amount) {
        if (fundHoldRepository.findByOrderId(orderId).isPresent()) {
            log.info("Hold already exists for order {}, skipping (idempotent replay of order.placed)", orderId);
            return;
        }

        getOrCreateWallet(userId);
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId).orElseThrow();

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            log.warn("Insufficient available balance for user {} to freeze {} for order {} (available: {}). " +
                            "No synchronous pre-trade balance check exists yet, so the order was already accepted.",
                    userId, amount, orderId, wallet.getAvailableBalance());
            return;
        }

        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(amount));
        wallet.setHeldBalance(wallet.getHeldBalance().add(amount));
        walletRepository.save(wallet);

        fundHoldRepository.save(FundHold.builder()
                .id(UUID.randomUUID())
                .walletId(wallet.getId())
                .orderId(orderId)
                .amount(amount)
                .status(FundHoldStatus.ACTIVE)
                .build());
    }

    /** Releases an ACTIVE hold back to available balance. No-op if already released/settled or never held. */
    @Transactional
    public void release(UUID orderId) {
        Optional<FundHold> holdOpt = fundHoldRepository.findByOrderId(orderId);
        if (holdOpt.isEmpty() || holdOpt.get().getStatus() != FundHoldStatus.ACTIVE) {
            log.info("No ACTIVE hold to release for order {} (either never held, or already released/settled)", orderId);
            return;
        }
        FundHold hold = holdOpt.get();
        Wallet wallet = walletRepository.findByIdForUpdate(hold.getWalletId()).orElseThrow();

        wallet.setHeldBalance(wallet.getHeldBalance().subtract(hold.getAmount()));
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(hold.getAmount()));
        walletRepository.save(wallet);

        hold.setStatus(FundHoldStatus.RELEASED);
        fundHoldRepository.save(hold);
    }

    /**
     * Settles one trade: converts the buyer's hold into a real debit, and
     * credits the seller directly (wallet-service has no concept of share
     * holdings to freeze on the sell side -- that's portfolio-service's job,
     * not built yet). Locks both wallets in a fixed order (by userId) to
     * avoid a lock-ordering deadlock against a concurrent trade where the
     * same two users are matched in the opposite role.
     */
    @Transactional
    public void settleTrade(UUID tradeId, UUID takerOrderId, UUID buyUserId, UUID sellUserId, BigDecimal amount) {
        getOrCreateWallet(buyUserId);
        getOrCreateWallet(sellUserId);

        boolean buyerFirst = buyUserId.compareTo(sellUserId) <= 0;
        UUID firstUserId = buyerFirst ? buyUserId : sellUserId;
        UUID secondUserId = buyerFirst ? sellUserId : buyUserId;

        Wallet firstWallet = walletRepository.findByUserIdForUpdate(firstUserId).orElseThrow();
        Wallet secondWallet = walletRepository.findByUserIdForUpdate(secondUserId).orElseThrow();

        Wallet buyerWallet = buyerFirst ? firstWallet : secondWallet;
        Wallet sellerWallet = buyerFirst ? secondWallet : firstWallet;

        settleBuyerSide(tradeId, takerOrderId, buyerWallet, amount);
        settleSellerSide(tradeId, sellerWallet, amount);
    }

    private void settleBuyerSide(UUID tradeId, UUID takerOrderId, Wallet buyerWallet, BigDecimal amount) {
        if (ledgerService.alreadyRecorded(buyerWallet.getId(), LedgerEntryType.DEBIT, tradeId)) {
            log.info("Trade {} already settled for buyer wallet {}, skipping (idempotent replay)", tradeId, buyerWallet.getId());
            return;
        }

        // takerOrderId is only meaningful here if the BUY side was the taker
        // (TradeExecutedEvent carries a single orderId -- the taker's -- see
        // Step 16's documented contract limitation). If the buyer was the
        // maker instead, this lookup misses and we fall back to a direct
        // debit below rather than guessing at an order id we don't have.
        Optional<FundHold> holdOpt = fundHoldRepository.findByOrderId(takerOrderId);
        if (holdOpt.isPresent() && holdOpt.get().getStatus() == FundHoldStatus.ACTIVE
                && holdOpt.get().getWalletId().equals(buyerWallet.getId())) {
            FundHold hold = holdOpt.get();
            buyerWallet.setHeldBalance(buyerWallet.getHeldBalance().subtract(hold.getAmount()));
            hold.setStatus(FundHoldStatus.SETTLED);
            fundHoldRepository.save(hold);
        } else {
            log.warn("Trade {}: no resolvable FundHold for buyer wallet {} (taker order {}); debiting available " +
                            "balance directly. This happens when the buyer was the maker side of the trade -- " +
                            "TradeExecutedEvent only carries the taker's orderId (known contract gap from Step 16).",
                    tradeId, buyerWallet.getId(), takerOrderId);
            buyerWallet.setAvailableBalance(buyerWallet.getAvailableBalance().subtract(amount));
        }

        walletRepository.save(buyerWallet);
        ledgerService.recordEntry(buyerWallet.getId(), LedgerEntryType.DEBIT, amount, "TRADE", tradeId,
                "Trade settlement (buy side)");
    }

    private void settleSellerSide(UUID tradeId, Wallet sellerWallet, BigDecimal amount) {
        if (ledgerService.alreadyRecorded(sellerWallet.getId(), LedgerEntryType.CREDIT, tradeId)) {
            log.info("Trade {} already settled for seller wallet {}, skipping (idempotent replay)", tradeId, sellerWallet.getId());
            return;
        }
        sellerWallet.setAvailableBalance(sellerWallet.getAvailableBalance().add(amount));
        walletRepository.save(sellerWallet);
        ledgerService.recordEntry(sellerWallet.getId(), LedgerEntryType.CREDIT, amount, "TRADE", tradeId,
                "Trade settlement (sell side)");
    }

    /** Test/manual-funding convenience -- not part of the Step 17 spec, added so freeze() has real funds to work with. */
    @Transactional
    public Wallet deposit(UUID userId, BigDecimal amount) {
        getOrCreateWallet(userId);
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId).orElseThrow();
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
        walletRepository.save(wallet);
        ledgerService.recordEntry(wallet.getId(), LedgerEntryType.CREDIT, amount, "DEPOSIT", UUID.randomUUID(),
                "Manual/test deposit");
        return wallet;
    }
}
