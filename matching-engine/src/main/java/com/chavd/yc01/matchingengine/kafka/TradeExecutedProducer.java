package com.chavd.yc01.matchingengine.kafka;

import com.chavd.yc01.common.dto.event.TradeExecutedEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Publishes fills to trade.executed. Per CLAUDE.md's Kafka topic table this is
 * keyed by user_id -- a trade inherently has two users (buyer and seller), so
 * this keys by buyUserId. That means the seller's own per-user ordering isn't
 * guaranteed on the same partition as their other trades; a production system
 * would likely fan this out into two per-user projections downstream instead
 * of trying to satisfy both sides with one partition key. Worth naming
 * explicitly as a simplification if asked.
 */
public class TradeExecutedProducer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TradeExecutedProducer.class);
    private static final String TOPIC = "trade.executed";

    private final Producer<String, String> producer;

    public TradeExecutedProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        this.producer = new KafkaProducer<>(props);
    }

    public void publish(TradeExecutedEvent trade) {
        try {
            String payload = EventCodec.MAPPER.writeValueAsString(trade);
            producer.send(new ProducerRecord<>(TOPIC, trade.getBuyUserId().toString(), payload),
                    (metadata, exception) -> {
                        if (exception != null) {
                            log.error("Failed to publish trade {} to {}", trade.getTradeId(), TOPIC, exception);
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize trade {}", trade.getTradeId(), e);
        }
    }

    @Override
    public void close() {
        producer.close();
    }
}
