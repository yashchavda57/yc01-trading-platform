package com.chavd.yc01.orderservice.service;

import com.chavd.yc01.common.dto.event.OrderPlacedEvent;
import com.chavd.yc01.common.dto.event.OrderStatus;
import com.chavd.yc01.common.dto.event.OrderType;
import com.chavd.yc01.common.exception.ResourceNotFoundException;
import com.chavd.yc01.common.exception.ValidationException;
import com.chavd.yc01.orderservice.dto.OrderResponse;
import com.chavd.yc01.orderservice.dto.PlaceOrderRequest;
import com.chavd.yc01.orderservice.entity.Order;
import com.chavd.yc01.orderservice.entity.OutboxEvent;
import com.chavd.yc01.orderservice.repository.OrderRepository;
import com.chavd.yc01.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the write side of the order lifecycle: place and cancel. Every state
 * change that other services need to know about is recorded as an OutboxEvent
 * row in the SAME transaction as the Order row itself (see class javadoc on
 * OutboxEvent) — OutboxPollerService is the only thing that actually talks to
 * Kafka.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String ORDER_PLACED_TOPIC = "order.placed";
    private static final Set<OrderType> PRICED_ORDER_TYPES =
            Set.of(OrderType.LIMIT, OrderType.STOP_LOSS, OrderType.STOP_LIMIT, OrderType.GTC);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyService idempotencyService;
    private final JsonMapper jsonMapper;

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyService.findExistingOrderId(idempotencyKey)
                    .flatMap(orderRepository::findById);
            if (cached.isPresent()) {
                log.info("Idempotent replay for key {}, returning existing order {}",
                        idempotencyKey, cached.get().getId());
                return OrderResponse.from(cached.get());
            }
        }

        if (PRICED_ORDER_TYPES.contains(request.orderType()) && request.price() == null) {
            throw new ValidationException(request.orderType() + " orders require a price");
        }

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(request.userId())
                .symbol(request.symbol())
                .side(request.side())
                .orderType(request.orderType())
                .quantity(request.quantity())
                .price(request.price())
                .status(OrderStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            order = orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            // Two requests with the same Idempotency-Key raced past the Redis
            // check above at the same time; the DB unique index is the real
            // guarantee. Whoever loses the race reads back the winner's row.
            log.warn("Idempotency key {} collided at the DB constraint, returning the winner's order",
                    idempotencyKey);
            return orderRepository.findByIdempotencyKey(idempotencyKey)
                    .map(OrderResponse::from)
                    .orElseThrow(() -> e);
        }

        order.transitionTo(OrderStatus.PLACED);

        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .orderType(order.getOrderType())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .timestamp(Instant.now())
                .build();

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("ORDER")
                .aggregateId(order.getId())
                .topic(ORDER_PLACED_TOPIC)
                .partitionKey(order.getSymbol())
                .payload(jsonMapper.writeValueAsString(event))
                .build();
        outboxEventRepository.save(outboxEvent);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.remember(idempotencyKey, order.getId());
        }

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No such order: " + orderId));
        try {
            order.transitionTo(OrderStatus.CANCELLED);
        } catch (IllegalStateException e) {
            throw new ValidationException(e.getMessage());
        }
        return OrderResponse.from(order);
    }
}
