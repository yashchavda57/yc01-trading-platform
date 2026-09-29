package com.chavd.yc01.matchingengine;

import com.chavd.yc01.matchingengine.engine.MatchingEngine;
import com.chavd.yc01.matchingengine.kafka.OrderPlacedConsumer;
import com.chavd.yc01.matchingengine.kafka.TradeExecutedProducer;
import com.chavd.yc01.matchingengine.snapshot.OrderBookSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plain Java entry point -- no Spring Boot here (per CLAUDE.md: "pure Java, no
 * framework overhead"). Config comes from environment variables with the same
 * defaults the Spring-based services use, so this runs against the same
 * docker-compose infra with zero extra setup.
 */
public final class MatchingEngineApplication {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngineApplication.class);

    public static void main(String[] args) {
        String bootstrapServers = env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String jdbcUrl = env("MATCHING_ENGINE_DB_URL", "jdbc:postgresql://localhost:5432/trading_matching_engine");
        String dbUsername = env("DB_USERNAME", "trading_user");
        String dbPassword = env("DB_PASSWORD", "trading_pass");
        long snapshotIntervalSeconds = Long.parseLong(env("SNAPSHOT_INTERVAL_SECONDS", "30"));

        MatchingEngine matchingEngine = new MatchingEngine();
        OrderBookSnapshotService snapshotService =
                new OrderBookSnapshotService(jdbcUrl, dbUsername, dbPassword, matchingEngine);
        snapshotService.restoreAll();
        snapshotService.startPeriodicSnapshots(snapshotIntervalSeconds);

        TradeExecutedProducer tradeExecutedProducer = new TradeExecutedProducer(bootstrapServers);
        OrderPlacedConsumer consumer = new OrderPlacedConsumer(
                bootstrapServers, "matching-engine", matchingEngine, tradeExecutedProducer);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down matching-engine...");
            consumer.shutdown();
            snapshotService.snapshotAll();
            snapshotService.shutdown();
            tradeExecutedProducer.close();
        }, "matching-engine-shutdown"));

        log.info("matching-engine starting, bootstrap-servers={}, db={}", bootstrapServers, jdbcUrl);
        consumer.run();
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private MatchingEngineApplication() {
    }
}
