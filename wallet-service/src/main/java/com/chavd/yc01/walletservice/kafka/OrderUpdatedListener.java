package com.chavd.yc01.walletservice.kafka;

import com.chavd.yc01.common.dto.event.OrderStatus;
import com.chavd.yc01.common.dto.event.OrderUpdatedEvent;
import com.chavd.yc01.walletservice.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Releases a hold on CANCELLED/REJECTED. NOTE: as of Step 17, nothing in the
 * system actually publishes order.updated yet (matching-engine doesn't emit
 * it, order-service's cancelOrder doesn't either) -- this listener is
 * contract-correct but has no live producer traffic to react to today. It was
 * verified by hand-publishing test messages directly to this topic (same
 * technique used to verify matching-engine in Step 16), not via an
 * end-to-end flow through order-service. Wiring a real producer is a
 * follow-up, tracked in this step's journal entry rather than silently
 * assumed to already work.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderUpdatedListener {

    private final WalletService walletService;

    @KafkaListener(topics = "order.updated", containerFactory = "orderUpdatedListenerFactory")
    public void onOrderUpdated(OrderUpdatedEvent event) {
        if (event.getStatus() == OrderStatus.CANCELLED || event.getStatus() == OrderStatus.REJECTED) {
            walletService.release(event.getOrderId());
        } else {
            log.debug("Order {} updated to {}, no wallet action needed", event.getOrderId(), event.getStatus());
        }
    }
}
