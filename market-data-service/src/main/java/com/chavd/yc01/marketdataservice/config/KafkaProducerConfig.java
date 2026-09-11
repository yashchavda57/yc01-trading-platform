package com.chavd.yc01.marketdataservice.config;

import com.chavd.yc01.common.dto.event.MarketTickEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Injects Spring Boot's autoconfigured Jackson 3 JsonMapper (java.time support
     * is built into Jackson 3's databind, no JavaTimeModule needed) instead of
     * letting JacksonJsonSerializer build its own bare JsonMapper.
     */
    @Bean
    public ProducerFactory<String, MarketTickEvent> tickProducerFactory(JsonMapper jsonMapper) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all"
        );
        JacksonJsonSerializer<MarketTickEvent> valueSerializer = new JacksonJsonSerializer<>(jsonMapper);
        valueSerializer.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, MarketTickEvent> tickKafkaTemplate(ProducerFactory<String, MarketTickEvent> tickProducerFactory) {
        return new KafkaTemplate<>(tickProducerFactory);
    }
}