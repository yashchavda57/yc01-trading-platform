package com.chavd.yc01.orderservice.entity;

import com.chavd.yc01.common.dto.event.OrderSide;
import com.chavd.yc01.common.dto.event.OrderStatus;
import com.chavd.yc01.common.dto.event.OrderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Column(nullable = false)
    private long quantity;

    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "filled_quantity", nullable = false)
    @Builder.Default
    private long filledQuantity = 0L;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Enforces the state machine: only well-defined transitions are allowed,
     * so e.g. FILLED -> PLACED fails here instead of silently corrupting state.
     */
    public void transitionTo(OrderStatus next) {
        boolean allowed = switch (status) {
            case PENDING -> next == OrderStatus.PLACED || next == OrderStatus.REJECTED;
            case PLACED -> next == OrderStatus.PARTIAL_FILL
                    || next == OrderStatus.FILLED
                    || next == OrderStatus.CANCELLED;
            case PARTIAL_FILL -> next == OrderStatus.PARTIAL_FILL
                    || next == OrderStatus.FILLED
                    || next == OrderStatus.CANCELLED;
            case FILLED, CANCELLED, REJECTED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException(
                    "Invalid order state transition: " + status + " -> " + next);
        }
        this.status = next;
    }
}
