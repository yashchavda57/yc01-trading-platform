package com.chavd.yc01.walletservice.config;

import com.chavd.yc01.common.dto.event.OrderPlacedEvent;
import com.chavd.yc01.common.dto.event.OrderUpdatedEvent;
import com.chavd.yc01.common.dto.event.TradeExecutedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.Map;

/**
 * wallet-service is the first service in this project to consume via
 * @KafkaListener (order-service and market-data-service only produce;
 * matching-engine uses the raw kafka-clients consumer, not spring-kafka).
 * Three separate typed container factories, one per event class, since
 * JacksonJsonDeserializer needs to know its target type up front (type headers
 * are disabled -- producers across this project write plain JSON with no
 * embedded Java type info). ErrorHandlingDeserializer wraps each one so a
 * genuinely unparseable message (a poison pill) fails just that record
 * instead of killing the whole listener container -- CLAUDE.md calls for a
 * real DLQ per consumer eventually; this at least contains the blast radius
 * for now.
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderPlacedEvent> orderPlacedListenerFactory() {
        return containerFactory(OrderPlacedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderUpdatedEvent> orderUpdatedListenerFactory() {
        return containerFactory(OrderUpdatedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TradeExecutedEvent> tradeExecutedListenerFactory() {
        return containerFactory(TradeExecutedEvent.class);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> containerFactory(Class<T> targetType) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory(targetType));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    private <T> ConsumerFactory<String, T> consumerFactory(Class<T> targetType) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        JacksonJsonDeserializer<T> jsonDeserializer = new JacksonJsonDeserializer<>(targetType);
        jsonDeserializer.addTrustedPackages("com.chavd.yc01.common.dto.event");
        jsonDeserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(jsonDeserializer));
    }
}
