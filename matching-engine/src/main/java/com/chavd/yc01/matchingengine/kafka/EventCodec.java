package com.chavd.yc01.matchingengine.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * This module has no Spring Boot autoconfiguration to hand it a
 * JavaTimeModule-registered ObjectMapper for free (see market-data-service's
 * KafkaProducerConfig javadoc for the same gotcha) -- register it explicitly,
 * once, here. WRITE_DATES_AS_TIMESTAMPS must be disabled explicitly too: a
 * bare ObjectMapper + JavaTimeModule still defaults to numeric epoch
 * timestamps, whereas Spring Boot's autoconfigured ObjectMapper (used by
 * order-service and market-data-service) disables it automatically -- without
 * this, matching-engine would be the one service on the whole event bus
 * writing Instant fields differently from everyone else.
 */
public final class EventCodec {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

    private EventCodec() {
    }
}
