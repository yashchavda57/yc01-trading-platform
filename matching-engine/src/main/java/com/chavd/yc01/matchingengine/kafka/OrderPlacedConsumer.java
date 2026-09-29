package com.chavd.yc01.matchingengine.kafka;

import com.chavd.yc01.common.dto.event.OrderPlacedEvent;
import com.chavd.yc01.common.dto.event.TradeExecutedEvent;
import com.chavd.yc01.matchingengine.engine.MatchingEngine;
import com.chavd.yc01.matchingengine.orderbook.IncomingOrder;
import com.chavd.yc01.matchingengine.orderbook.MatchResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * order-service's OutboxPollerService publishes order.placed at-least-once
 * (see Step 15's journal entry): a crash between a successful send and the
 * outbox row being marked published means the same order can arrive twice.
 * This consumer does NOT currently dedup on orderId before matching -- a
 * replayed order.placed would be matched a second time. That's a real gap
 * (idempotent consumption is the whole point of pairing with at-least-once
 * delivery) worth calling out rather than silently ignoring; a follow-up
 * step would track processed orderIds (e.g. in Postgres alongside the
 * snapshot table) and skip ones already seen.
 */
public class OrderPlacedConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedConsumer.class);
    private static final String TOPIC = "order.placed";

    private final KafkaConsumer<String, String> consumer;
    private final MatchingEngine matchingEngine;
    private final TradeExecutedProducer tradeExecutedProducer;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public OrderPlacedConsumer(String bootstrapServers, String groupId,
                                MatchingEngine matchingEngine, TradeExecutedProducer tradeExecutedProducer) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        this.consumer = new KafkaConsumer<>(props);
        this.matchingEngine = matchingEngine;
        this.tradeExecutedProducer = tradeExecutedProducer;
    }

    public void run() {
        consumer.subscribe(List.of(TOPIC));
        log.info("OrderPlacedConsumer subscribed to {}", TOPIC);
        try {
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    handle(record);
                }
                if (!records.isEmpty()) {
                    consumer.commitSync();
                }
            }
        } catch (WakeupException e) {
            if (running.get()) {
                throw e; // a genuine wakeup, not our own shutdown()
            }
        } finally {
            consumer.close();
        }
    }

    private void handle(ConsumerRecord<String, String> record) {
        try {
            OrderPlacedEvent event = EventCodec.MAPPER.readValue(record.value(), OrderPlacedEvent.class);
            IncomingOrder incoming = new IncomingOrder(
                    event.getOrderId(), event.getUserId(), event.getSymbol(),
                    event.getSide(), event.getOrderType(), event.getPrice(), event.getQuantity());

            MatchResult result = matchingEngine.process(incoming);
            for (TradeExecutedEvent trade : result.trades()) {
                tradeExecutedProducer.publish(trade);
            }
            log.info("Processed order {} ({} {} {}@{}): {} trade(s), {} remaining",
                    event.getOrderId(), event.getSide(), event.getQuantity(), event.getSymbol(),
                    event.getPrice(), result.trades().size(), result.remainingQuantity());
        } catch (Exception e) {
            log.error("Failed to process order.placed record at offset {}: {}", record.offset(), record.value(), e);
        }
    }

    public void shutdown() {
        running.set(false);
        consumer.wakeup();
    }

    @Override
    public void close() {
        shutdown();
    }
}
