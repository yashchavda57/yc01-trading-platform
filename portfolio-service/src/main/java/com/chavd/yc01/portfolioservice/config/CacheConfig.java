package com.chavd.yc01.portfolioservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * L1 only -- the Redis L2 is wired directly in PortfolioReadService via
 * StringRedisTemplate, not through this CacheManager, since the two layers
 * have different TTLs and the write side needs to push to L2 immediately on
 * a trade (write-through) while L1 is left to just expire per-JVM. Using
 * Spring's generic CacheManager/Cache abstraction here (get/put called
 * directly from PortfolioReadService) rather than @Cacheable annotations,
 * because the read-through chain (L1 miss -> L2 -> Postgres, populating
 * both on the way back up) doesn't map onto a single annotated method.
 */
@Configuration
public class CacheConfig {

    private static final String HOLDINGS_CACHE = "holdings";

    @Value("${portfolio.cache.l1-ttl-seconds:30}")
    private long l1TtlSeconds;

    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(HOLDINGS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(l1TtlSeconds))
                .maximumSize(10_000));
        return manager;
    }

    @Bean
    public Cache holdingsL1Cache(CacheManager caffeineCacheManager) {
        return caffeineCacheManager.getCache(HOLDINGS_CACHE);
    }
}
