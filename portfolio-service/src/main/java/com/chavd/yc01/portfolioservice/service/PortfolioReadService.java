package com.chavd.yc01.portfolioservice.service;

import com.chavd.yc01.portfolioservice.dto.HoldingResponse;
import com.chavd.yc01.portfolioservice.dto.PnlResponse;
import com.chavd.yc01.portfolioservice.dto.SymbolPnl;
import com.chavd.yc01.portfolioservice.repository.HoldingRepository;
import com.chavd.yc01.portfolioservice.repository.RealizedPnlEntryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The CQRS read side. getHoldings() is a read-through chain -- Caffeine (L1,
 * per-JVM, 30s) -> Redis (L2, shared across instances, 5min) -> Postgres
 * (source of truth, on a full cache miss) -- populating every layer above
 * wherever the answer was actually found. PortfolioWriteService also calls
 * refreshCache() directly (write-through) right after a trade commits, so in
 * practice Redis is rarely more than a moment stale; the TTLs above are the
 * worst-case bound, not the typical case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioReadService {

    private static final String PRICE_KEY_PREFIX = "price:";
    private static final String HOLDINGS_KEY_PREFIX = "portfolio:holdings:";

    private final HoldingRepository holdingRepository;
    private final RealizedPnlEntryRepository realizedPnlEntryRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Cache holdingsL1Cache;

    @Value("${portfolio.cache.l2-ttl-minutes:5}")
    private long l2TtlMinutes;

    public List<HoldingResponse> getHoldings(UUID userId) {
        String cacheKey = userId.toString();

        Cache.ValueWrapper l1Hit = holdingsL1Cache.get(cacheKey);
        if (l1Hit != null) {
            return castHoldings(l1Hit.get());
        }

        Optional<List<HoldingResponse>> l2Hit = readFromRedis(userId);
        if (l2Hit.isPresent()) {
            holdingsL1Cache.put(cacheKey, l2Hit.get());
            return l2Hit.get();
        }

        List<HoldingResponse> fromDb = holdingRepository.findByUserId(userId).stream()
                .map(HoldingResponse::from)
                .toList();
        writeToRedis(userId, fromDb);
        holdingsL1Cache.put(cacheKey, fromDb);
        return fromDb;
    }

    /**
     * Write-through: called by PortfolioWriteService right after a trade
     * commits, so Redis (and this instance's own L1) reflect the new holding
     * immediately instead of waiting out the TTL. Other service instances'
     * L1 caches don't get touched by this -- they're per-JVM -- but they'll
     * read the now-fresh Redis entry on their own next L1 miss, at most
     * l1TtlSeconds later.
     */
    public void refreshCache(UUID userId) {
        List<HoldingResponse> holdings = holdingRepository.findByUserId(userId).stream()
                .map(HoldingResponse::from)
                .toList();
        writeToRedis(userId, holdings);
        holdingsL1Cache.put(userId.toString(), holdings);
    }

    public PnlResponse getPnl(UUID userId) {
        List<HoldingResponse> holdings = getHoldings(userId);
        List<SymbolPnl> symbolPnls = new ArrayList<>();
        BigDecimal unrealizedTotal = BigDecimal.ZERO;

        for (HoldingResponse holding : holdings) {
            BigDecimal livePrice = getLivePrice(holding.symbol());
            boolean priceKnown = livePrice != null;
            if (!priceKnown) {
                log.warn("No live price cached for symbol {} ({}{} missing or expired in Redis); " +
                                "reporting unrealized P&L for this holding as zero",
                        holding.symbol(), PRICE_KEY_PREFIX, holding.symbol());
            }
            BigDecimal effectivePrice = priceKnown ? livePrice : holding.avgCostBasis();
            BigDecimal unrealized = effectivePrice.subtract(holding.avgCostBasis())
                    .multiply(BigDecimal.valueOf(holding.quantity()));
            unrealizedTotal = unrealizedTotal.add(unrealized);

            symbolPnls.add(SymbolPnl.builder()
                    .symbol(holding.symbol())
                    .quantity(holding.quantity())
                    .avgCostBasis(holding.avgCostBasis())
                    .currentPrice(effectivePrice)
                    .unrealizedPnl(unrealized)
                    .build());
        }

        BigDecimal realized = realizedPnlEntryRepository.sumRealizedPnlByUserId(userId);
        return PnlResponse.builder()
                .userId(userId)
                .realizedPnl(realized)
                .unrealizedPnl(unrealizedTotal)
                .totalPnl(realized.add(unrealizedTotal))
                .holdings(symbolPnls)
                .build();
    }

    private BigDecimal getLivePrice(String symbol) {
        String raw = redisTemplate.opsForValue().get(PRICE_KEY_PREFIX + symbol);
        return raw != null ? new BigDecimal(raw) : null;
    }

    private Optional<List<HoldingResponse>> readFromRedis(UUID userId) {
        String json = redisTemplate.opsForValue().get(HOLDINGS_KEY_PREFIX + userId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, new TypeReference<List<HoldingResponse>>() {
            }));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached holdings for user {}, falling back to Postgres", userId, e);
            return Optional.empty();
        }
    }

    private void writeToRedis(UUID userId, List<HoldingResponse> holdings) {
        try {
            String json = objectMapper.writeValueAsString(holdings);
            redisTemplate.opsForValue().set(HOLDINGS_KEY_PREFIX + userId, json, Duration.ofMinutes(l2TtlMinutes));
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache holdings for user {} in Redis", userId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<HoldingResponse> castHoldings(Object cached) {
        return (List<HoldingResponse>) cached;
    }
}
