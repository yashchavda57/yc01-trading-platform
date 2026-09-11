package com.chavd.yc01.marketdataservice.service;

import com.chavd.yc01.common.dto.event.MarketTickEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

/**
 * Plain blocking JDBC insert into the TimescaleDB "ticks" hypertable. This
 * deliberately runs off the reactive request path (only ever called from the
 * @Scheduled tick generator's own thread), so a blocking JdbcTemplate call here
 * doesn't tie up the WebFlux event loop the way it would inside a WebSocketHandler.
 */
@Service
@RequiredArgsConstructor
public class TickPersistenceService {

    private static final String INSERT_TICK_SQL =
            "INSERT INTO ticks (symbol, price, volume, ts) VALUES (?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public void save(MarketTickEvent tick) {
        jdbcTemplate.update(
                INSERT_TICK_SQL,
                tick.getSymbol(),
                tick.getPrice(),
                tick.getVolume(),
                Timestamp.from(tick.getTimestamp())
        );
    }
}
