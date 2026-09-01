package com.chavd.yc01.common.dto.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventDtoBuilderTest {

    @Test
    void marketTickEvent_builderPopulatesAllFields() {
        Instant now = Instant.now();

        MarketTickEvent event = MarketTickEvent.builder()
                .symbol("AAPL")
                .price(new BigDecimal("189.50"))
                .volume(1200L)
                .timestamp(now)
                .build();

        assertEquals("AAPL", event.getSymbol());
        assertEquals(new BigDecimal("189.50"), event.getPrice());
        assertEquals(1200L, event.getVolume());
        assertEquals(now, event.getTimestamp());
    }

    @Test
    void orderPlacedEvent_builderPopulatesAllFields() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .orderId(orderId)
                .userId(userId)
                .symbol("AAPL")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .quantity(10L)
                .price(new BigDecimal("189.50"))
                .timestamp(now)
                .build();

        assertEquals(orderId, event.getOrderId());
        assertEquals(userId, event.getUserId());
        assertEquals("AAPL", event.getSymbol());
        assertEquals(OrderSide.BUY, event.getSide());
        assertEquals(OrderType.LIMIT, event.getOrderType());
        assertEquals(10L, event.getQuantity());
        assertEquals(new BigDecimal("189.50"), event.getPrice());
        assertEquals(now, event.getTimestamp());
    }

    @Test
    void orderUpdatedEvent_builderPopulatesAllFields() {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();

        OrderUpdatedEvent event = OrderUpdatedEvent.builder()
                .orderId(orderId)
                .status(OrderStatus.PARTIAL_FILL)
                .filledQuantity(4L)
                .timestamp(now)
                .build();

        assertEquals(orderId, event.getOrderId());
        assertEquals(OrderStatus.PARTIAL_FILL, event.getStatus());
        assertEquals(4L, event.getFilledQuantity());
        assertEquals(now, event.getTimestamp());
    }

    @Test
    void tradeExecutedEvent_builderPopulatesAllFields() {
        UUID tradeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID buyUserId = UUID.randomUUID();
        UUID sellUserId = UUID.randomUUID();
        Instant now = Instant.now();

        TradeExecutedEvent event = TradeExecutedEvent.builder()
                .tradeId(tradeId)
                .orderId(orderId)
                .buyUserId(buyUserId)
                .sellUserId(sellUserId)
                .symbol("AAPL")
                .quantity(10L)
                .price(new BigDecimal("189.50"))
                .timestamp(now)
                .build();

        assertEquals(tradeId, event.getTradeId());
        assertEquals(orderId, event.getOrderId());
        assertEquals(buyUserId, event.getBuyUserId());
        assertEquals(sellUserId, event.getSellUserId());
        assertEquals("AAPL", event.getSymbol());
        assertEquals(10L, event.getQuantity());
        assertEquals(new BigDecimal("189.50"), event.getPrice());
        assertEquals(now, event.getTimestamp());
    }
}
