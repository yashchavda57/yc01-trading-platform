package com.chavd.yc01.walletservice.kafka;

import com.chavd.yc01.common.dto.event.OrderPlacedEvent;
import com.chavd.yc01.common.dto.event.OrderSide;
import com.chavd.yc01.walletservice.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Freezes cash against BUY orders only. SELL orders would need to freeze the
 * shares being sold, not cash -- that's a position hold, which is
 * portfolio-service's job (not built yet, Phase 3). Documented as a
 * deliberate scope boundary, not an oversight.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPlacedListener {

    private final WalletService walletService;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(topics = "order.placed", containerFactory = "orderPlacedListenerFactory")
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (event.getSide() != OrderSide.BUY) {
            log.info("Order {} is a SELL, no cash to freeze (position holds belong to portfolio-service)", event.getOrderId());
            return;
        }

        BigDecimal price = resolvePrice(event);
        if (price == null) {
            log.warn("Could not resolve a price for MARKET order {} on {} (no price on the event, " +
                    "no price:{} in Redis) -- skipping fund freeze", event.getOrderId(), event.getSymbol(), event.getSymbol());
            return;
        }

        BigDecimal amount = price.multiply(BigDecimal.valueOf(event.getQuantity()));
        walletService.freeze(event.getUserId(), event.getOrderId(), amount);
    }

    private BigDecimal resolvePrice(OrderPlacedEvent event) {
        if (event.getPrice() != null) {
            return event.getPrice();
        }
        // MARKET order: no limit price on the event itself, so fall back to
        // market-data-service's last-traded-price cache for an estimate.
        String cached = redisTemplate.opsForValue().get("price:" + event.getSymbol());
        return cached == null ? null : new BigDecimal(cached);
    }
}
