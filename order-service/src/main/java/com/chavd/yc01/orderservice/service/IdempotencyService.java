package com.chavd.yc01.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Fast-path dedup check on the client-supplied Idempotency-Key header, same
 * pattern as user-service's refresh-token store. This is a cache, not the
 * source of truth: the "orders.idempotency_key" unique index in Postgres is
 * what actually guarantees no duplicate ever gets created, in case two
 * requests with the same key race past this check at the same time.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:order:";

    private final StringRedisTemplate redisTemplate;

    @Value("${order.idempotency-ttl-hours:24}")
    private long ttlHours;

    public Optional<UUID> findExistingOrderId(String idempotencyKey) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
        return Optional.ofNullable(value).map(UUID::fromString);
    }

    public void remember(String idempotencyKey, UUID orderId) {
        redisTemplate.opsForValue().set(
                KEY_PREFIX + idempotencyKey,
                orderId.toString(),
                Duration.ofHours(ttlHours)
        );
    }
}
