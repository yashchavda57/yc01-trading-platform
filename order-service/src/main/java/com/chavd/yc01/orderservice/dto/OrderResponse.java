package com.chavd.yc01.orderservice.dto;

import com.chavd.yc01.common.dto.event.OrderSide;
import com.chavd.yc01.common.dto.event.OrderStatus;
import com.chavd.yc01.common.dto.event.OrderType;
import com.chavd.yc01.orderservice.entity.Order;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record OrderResponse(
        UUID id,
        UUID userId,
        String symbol,
        OrderSide side,
        OrderType orderType,
        long quantity,
        BigDecimal price,
        OrderStatus status,
        long filledQuantity,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .orderType(order.getOrderType())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .status(order.getStatus())
                .filledQuantity(order.getFilledQuantity())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
