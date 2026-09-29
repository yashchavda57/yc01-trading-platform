package com.chavd.yc01.matchingengine.benchmark;

import com.chavd.yc01.common.dto.event.OrderSide;
import com.chavd.yc01.common.dto.event.OrderType;
import com.chavd.yc01.matchingengine.orderbook.IncomingOrder;
import com.chavd.yc01.matchingengine.orderbook.OrderBook;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Measures matches/sec for a single instrument's OrderBook under the
 * ReentrantReadWriteLock strategy described in CLAUDE.md, so any claim about
 * throughput (vs. the >50k matches/sec target) is measured, not assumed.
 * <p>
 * Run with: java -cp target/matching-engine.jar org.openjdk.jmh.Main
 * <p>
 * Two scenarios:
 * - restingBookInsert: pure inserts, nothing ever crosses (worst case for the
 *   skip-list's insert cost, best case for lock contention since nothing walks
 *   multiple price levels).
 * - crossingMatch: every incoming order crosses and fully fills a resting
 *   order at the best price (exercises the full match+remove path).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MatchingEngineBenchmark {

    private OrderBook book;

    @Setup(Level.Iteration)
    public void setUp() {
        book = new OrderBook("BENCH");
        // Pre-seed one resting ask per price so crossingMatch always has
        // something to consume without the benchmark loop itself measuring
        // setup cost.
        for (int i = 0; i < 10_000; i++) {
            BigDecimal price = BigDecimal.valueOf(100 + (i % 500));
            book.match(new IncomingOrder(UUID.randomUUID(), UUID.randomUUID(), "BENCH",
                    OrderSide.SELL, OrderType.LIMIT, price, 1_000_000L)); // effectively never depletes
        }
    }

    @Benchmark
    @Threads(4)
    public void restingBookInsert(Blackhole blackhole) {
        // Priced well below every seeded ask (100-599) so this BUY never
        // crosses -- pure insert onto the bids side, matching loop never runs.
        BigDecimal price = BigDecimal.valueOf(1 + ThreadLocalRandom.current().nextInt(50));
        var result = book.match(new IncomingOrder(UUID.randomUUID(), UUID.randomUUID(), "BENCH",
                OrderSide.BUY, OrderType.LIMIT, price, 10));
        blackhole.consume(result);
    }

    @Benchmark
    @Threads(4)
    public void crossingMatch(Blackhole blackhole) {
        BigDecimal price = BigDecimal.valueOf(100 + ThreadLocalRandom.current().nextInt(500));
        var result = book.match(new IncomingOrder(UUID.randomUUID(), UUID.randomUUID(), "BENCH",
                OrderSide.BUY, OrderType.LIMIT, price, 10));
        blackhole.consume(result);
    }
}
