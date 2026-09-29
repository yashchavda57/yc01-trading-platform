package com.chavd.yc01.matchingengine.snapshot;

import com.chavd.yc01.common.dto.event.OrderSide;
import com.chavd.yc01.matchingengine.engine.MatchingEngine;
import com.chavd.yc01.matchingengine.kafka.EventCodec;
import com.chavd.yc01.matchingengine.orderbook.OrderBook;
import com.chavd.yc01.matchingengine.orderbook.RestingOrder;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically dumps every order book's resting orders to Postgres as one
 * JSON blob per symbol, and can rebuild the in-memory books from that on
 * startup. This is recovery, not durability-on-every-write: if the process
 * crashes between snapshots, whatever matched in that window is only
 * recoverable from replaying order.placed from Kafka (which this service does
 * NOT do) -- the snapshot just avoids having to replay the ENTIRE topic
 * history from the beginning every time the engine restarts.
 */
public class OrderBookSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(OrderBookSnapshotService.class);

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final MatchingEngine matchingEngine;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "orderbook-snapshot");
        t.setDaemon(true);
        return t;
    });

    public OrderBookSnapshotService(String jdbcUrl, String username, String password,
                                     MatchingEngine matchingEngine) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.matchingEngine = matchingEngine;
        ensureSchema();
    }

    private void ensureSchema() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS order_book_snapshots (
                    symbol       VARCHAR(16) PRIMARY KEY,
                    snapshot_json TEXT       NOT NULL,
                    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """;
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to ensure order_book_snapshots schema", e);
        }
    }

    public void startPeriodicSnapshots(long intervalSeconds) {
        scheduler.scheduleAtFixedRate(this::snapshotAll, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("Started periodic order book snapshots every {}s", intervalSeconds);
    }

    public void snapshotAll() {
        for (OrderBook book : matchingEngine.allBooks().values()) {
            try {
                snapshot(book);
            } catch (Exception e) {
                log.error("Failed to snapshot order book {}", book.getSymbol(), e);
            }
        }
    }

    private void snapshot(OrderBook book) throws Exception {
        List<SnapshotEntry> entries = book.allResting().stream()
                .map(o -> new SnapshotEntry(o.getOrderId(), o.getUserId(), o.getSide(), o.getPrice(), o.getRemainingQuantity()))
                .toList();
        String json = EventCodec.MAPPER.writeValueAsString(entries);

        String upsert = """
                INSERT INTO order_book_snapshots (symbol, snapshot_json, updated_at)
                VALUES (?, ?, now())
                ON CONFLICT (symbol) DO UPDATE SET snapshot_json = EXCLUDED.snapshot_json, updated_at = now()
                """;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, book.getSymbol());
            ps.setString(2, json);
            ps.executeUpdate();
        }
    }

    /** Rebuilds every OrderBook the snapshot table knows about. Call once, before consuming starts. */
    public void restoreAll() {
        String select = "SELECT symbol, snapshot_json FROM order_book_snapshots";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(select)) {
            int restoredBooks = 0;
            while (rs.next()) {
                String symbol = rs.getString("symbol");
                List<SnapshotEntry> entries = EventCodec.MAPPER.readValue(
                        rs.getString("snapshot_json"), new TypeReference<List<SnapshotEntry>>() {
                        });
                restoreInto(symbol, entries);
                restoredBooks++;
            }
            log.info("Restored {} order book(s) from snapshot", restoredBooks);
        } catch (SQLException | java.io.IOException e) {
            log.warn("Could not restore order book snapshots (starting empty): {}", e.getMessage());
        }
    }

    private void restoreInto(String symbol, List<SnapshotEntry> entries) {
        for (SnapshotEntry entry : entries) {
            RestingOrder restingOrder = new RestingOrder(
                    entry.orderId(), entry.userId(), symbol, entry.side(), entry.price(), entry.quantity());
            bookFor(symbol).restore(restingOrder);
        }
    }

    private OrderBook bookFor(String symbol) {
        // MatchingEngine only creates a book lazily via process(); snapshot
        // restore needs the same lazy-create behavior without going through
        // matching, so it reaches into the same map via computeIfAbsent.
        return matchingEngine.allBooks().computeIfAbsent(symbol, OrderBook::new);
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }
}
