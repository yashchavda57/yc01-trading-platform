package com.chavd.yc01.orderservice.dto;

import com.chavd.yc01.common.dto.event.OrderSide;
import com.chavd.yc01.common.dto.event.OrderType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceOrderRequest(
        @NotNull UUID userId,
        @NotNull String symbol,
        @NotNull OrderSide side,
        @NotNull OrderType orderType,
        @Positive long quantity,
        // Null for MARKET orders; required for LIMIT/STOP_LOSS/STOP_LIMIT/GTC,
        // enforced in OrderService rather than here since it depends on orderType.
        BigDecimal price
) {
}
