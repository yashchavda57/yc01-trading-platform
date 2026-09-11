package com.chavd.yc01.marketdataservice.service;

import com.chavd.yc01.common.dto.event.MarketTickEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates a live market feed: no real exchange data, just a per-symbol random
 * walk on price so the rest of the pipeline (Kafka, TimescaleDB, WebSocket push,
 * Redis cache) has something realistic to move through it.
 */
@Service
@RequiredArgsConstructor
public class TickGeneratorService {

    private static final BigDecimal SEED_PRICE = new BigDecimal("200.00");
    private static final double MAX_PCT_MOVE_PER_TICK = 0.005; // +/- 0.5%

    private final Map<String, BigDecimal> lastPrices = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private final TickPublisherService tickPublisherService;
    private final TickPersistenceService tickPersistenceService;
    private final PriceStreamService priceStreamService;

    @Value("${market-data.symbols:AAPL,GOOGL,MSFT,AMZN,TSLA}")
    private String symbolsConfig;

    private List<String> symbols;

    @PostConstruct
    void seed() {
        symbols = Arrays.stream(symbolsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        symbols.forEach(symbol -> lastPrices.put(symbol, SEED_PRICE));
    }

    @Scheduled(fixedRateString = "${market-data.tick-interval-ms:1000}")
    public void generateTicks() {
        for (String symbol : symbols) {
            MarketTickEvent tick = nextTick(symbol);
            tickPublisherService.publish(tick);
            tickPersistenceService.save(tick);
            priceStreamService.publish(tick);
        }
    }

    private MarketTickEvent nextTick(String symbol) {
        BigDecimal last = lastPrices.get(symbol);
        double pctChange = (random.nextDouble() * 2 - 1) * MAX_PCT_MOVE_PER_TICK;
        BigDecimal next = last.multiply(BigDecimal.valueOf(1 + pctChange))
                .setScale(2, RoundingMode.HALF_UP);
        if (next.compareTo(BigDecimal.ZERO) <= 0) {
            next = last;
        }
        lastPrices.put(symbol, next);

        long volume = 100 + random.nextInt(900);

        return MarketTickEvent.builder()
                .symbol(symbol)
                .price(next)
                .volume(volume)
                .timestamp(Instant.now())
                .build();
    }
}
