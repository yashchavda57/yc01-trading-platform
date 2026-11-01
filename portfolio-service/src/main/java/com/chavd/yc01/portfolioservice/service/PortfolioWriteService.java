package com.chavd.yc01.portfolioservice.service;

import com.chavd.yc01.common.dto.event.TradeExecutedEvent;
import com.chavd.yc01.portfolioservice.entity.Holding;
import com.chavd.yc01.portfolioservice.entity.RealizedPnlEntry;
import com.chavd.yc01.portfolioservice.repository.HoldingRepository;
import com.chavd.yc01.portfolioservice.repository.RealizedPnlEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The CQRS write side. A single trade.executed event touches two different
 * users' holdings (the buyer's and the seller's), which is the one thing
 * that makes this trickier than wallet-service's single-wallet-per-event
 * updates: two row locks are taken per event, not one, so lock order has to
 * be fixed to avoid a classic deadlock (two trades between the same pair of
 * users, in opposite buy/sell roles, racing to lock buyer-then-seller vs
 * seller-then-buyer at the same time). Locking in a userId-comparison order
 * that's the same regardless of which side of the trade a user is on
 * guarantees every concurrent transaction agrees on lock order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioWriteService {

    private final HoldingRepository holdingRepository;
    private final RealizedPnlEntryRepository realizedPnlEntryRepository;
    private final PortfolioReadService portfolioReadService;

    @Transactional
    public void applyTrade(TradeExecutedEvent event) {
        boolean buyerLocksFirst = event.getBuyUserId().compareTo(event.getSellUserId()) <= 0;
        if (buyerLocksFirst) {
            applyBuy(event);
            applySell(event);
        } else {
            applySell(event);
            applyBuy(event);
        }
    }

    private void applyBuy(TradeExecutedEvent event) {
        Holding holding = getOrCreateForUpdate(event.getBuyUserId(), event.getSymbol());

        long newQuantity = holding.getQuantity() + event.getQuantity();
        BigDecimal newAvgCostBasis = HoldingMath.newAvgCostBasis(
                holding.getQuantity(), holding.getAvgCostBasis(), event.getQuantity(), event.getPrice());

        holding.setQuantity(newQuantity);
        holding.setAvgCostBasis(newAvgCostBasis);
        holdingRepository.save(holding);

        portfolioReadService.refreshCache(event.getBuyUserId());
    }

    private void applySell(TradeExecutedEvent event) {
        // Idempotency guard first: a redelivered trade.executed must not book
        // the realized P&L (or decrement quantity) a second time.
        if (realizedPnlEntryRepository.existsByTradeId(event.getTradeId())) {
            log.info("Realized P&L already booked for trade {}, skipping (idempotent replay)", event.getTradeId());
            return;
        }

        Holding holding = getOrCreateForUpdate(event.getSellUserId(), event.getSymbol());

        // No pretrade check exists yet (risk-service, Phase 3, not built) to
        // guarantee a sell never exceeds what's actually held -- same known
        // limitation wallet-service documents for fund freezing. Clamping at
        // zero here keeps a bad/short sell from driving quantity negative;
        // it's logged loudly because it should never legitimately happen
        // once the matching engine only matches against real resting orders.
        long sellQuantity = event.getQuantity();
        if (sellQuantity > holding.getQuantity()) {
            log.warn("Trade {} sells {} of {} for user {} but only {} are held -- clamping to avoid a negative position; " +
                            "this should only happen before risk-service enforces real position limits",
                    event.getTradeId(), sellQuantity, event.getSymbol(), event.getSellUserId(), holding.getQuantity());
            sellQuantity = holding.getQuantity();
        }

        // A fully-clamped sell (held zero to begin with) has nothing to
        // record -- a RealizedPnlEntry with quantity 0 and realizedPnl 0 is
        // a zero-information fact that would only clutter what's meant to be
        // an auditable ledger. The warning above already captures that this
        // happened; skip both the entry and the (no-op) holding update.
        if (sellQuantity == 0) {
            log.warn("Trade {} for user {} resulted in a zero-quantity sell after clamping -- skipping realized P&L entry entirely",
                    event.getTradeId(), event.getSellUserId());
            return;
        }

        BigDecimal realizedPnl = HoldingMath.realizedPnl(event.getPrice(), holding.getAvgCostBasis(), sellQuantity);

        realizedPnlEntryRepository.save(RealizedPnlEntry.builder()
                .id(UUID.randomUUID())
                .userId(event.getSellUserId())
                .symbol(event.getSymbol())
                .tradeId(event.getTradeId())
                .quantity(sellQuantity)
                .sellPrice(event.getPrice())
                .avgCostBasisAtSale(holding.getAvgCostBasis())
                .realizedPnl(realizedPnl)
                .build());

        // avgCostBasis is deliberately untouched by a sell -- only quantity
        // moves. See Holding's javadoc for why.
        holding.setQuantity(holding.getQuantity() - sellQuantity);
        holdingRepository.save(holding);

        portfolioReadService.refreshCache(event.getSellUserId());
    }

    /**
     * Same race-handling shape as WalletService.getOrCreateWallet: a never-
     * before-seen (userId, symbol) can't be locked via SELECT ... FOR UPDATE
     * because there's no row yet, so two concurrent first-ever trades for
     * the same pair could both try to insert one. The loser's insert fails
     * the (user_id, symbol) unique index instead of creating a duplicate
     * row, and is handled by re-reading (now lockable) the winner's row.
     */
    private Holding getOrCreateForUpdate(UUID userId, String symbol) {
        return holdingRepository.findByUserIdAndSymbolForUpdate(userId, symbol)
                .orElseGet(() -> createHolding(userId, symbol));
    }

    private Holding createHolding(UUID userId, String symbol) {
        try {
            return holdingRepository.save(Holding.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .symbol(symbol)
                    .quantity(0)
                    .avgCostBasis(BigDecimal.ZERO)
                    .build());
        } catch (DataIntegrityViolationException e) {
            return holdingRepository.findByUserIdAndSymbolForUpdate(userId, symbol).orElseThrow();
        }
    }
}
