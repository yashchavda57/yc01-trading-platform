package com.chavd.yc01.marketdataservice.service;

import com.chavd.yc01.common.dto.event.MarketTickEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Fans out one generated tick to the two places anything downstream reads it
 * from: the market.ticks Kafka topic (keyed by symbol, for any consumer that
 * wants the full stream), and the Redis price:{symbol} key (the fast path
 * order-service will check for "current price" without touching Kafka or the DB).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TickPublisherService {

    private static final String TOPIC = "market.ticks";
    private static final String PRICE_KEY_PREFIX = "price:";

    private final KafkaTemplate<String, MarketTickEvent> tickKafkaTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${market-data.price-ttl-seconds:5}")
    private long priceTtlSeconds;

    public void publish(MarketTickEvent tick) {
        tickKafkaTemplate.send(TOPIC, tick.getSymbol(), tick)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish tick for {} to {}", tick.getSymbol(), TOPIC, ex);
                    }
                });

        redisTemplate.opsForValue().set(
                PRICE_KEY_PREFIX + tick.getSymbol(),
                tick.getPrice().toPlainString(),
                Duration.ofSeconds(priceTtlSeconds)
        );
    }
}
