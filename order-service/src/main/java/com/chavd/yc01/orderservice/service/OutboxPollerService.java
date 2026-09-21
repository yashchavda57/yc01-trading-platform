package com.chavd.yc01.orderservice.service;

import com.chavd.yc01.orderservice.entity.OutboxEvent;
import com.chavd.yc01.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The only piece of this service that talks to Kafka. Runs independently of
 * order placement: a request can fully succeed (Order + OutboxEvent both
 * committed) even if Kafka is completely down at that moment — this poller
 * just catches up whenever Kafka comes back. That's "at-least-once delivery":
 * a crash between send() succeeding and the row being marked published means
 * the same row gets sent again next tick, so consumers (matching-engine, in a
 * later step) must be idempotent on orderId rather than assuming exactly-once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPollerService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    @Scheduled(fixedRateString = "${order.outbox-poll-interval-ms:2000}")
    @Transactional
    public void publishUnpublishedEvents() {
        List<OutboxEvent> unpublished = outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : unpublished) {
            try {
                outboxKafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload()).get();
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
            } catch (Exception e) {
                log.error("Failed to publish outbox event {} to {}, will retry next poll",
                        event.getId(), event.getTopic(), e);
            }
        }
    }
}
