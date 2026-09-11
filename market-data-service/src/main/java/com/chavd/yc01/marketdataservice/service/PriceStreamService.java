package com.chavd.yc01.marketdataservice.service;

import com.chavd.yc01.common.dto.event.MarketTickEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-memory hub every generated tick is pushed into, and that WebSocket
 * subscribers read from. One multicast sink for the whole service — each
 * subscriber gets their own filtered view via streamFor(symbol), so adding a
 * new WebSocket connection never touches tick generation or Kafka publishing.
 */
@Service
public class PriceStreamService {

    private final Sinks.Many<MarketTickEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void publish(MarketTickEvent tick) {
        sink.tryEmitNext(tick);
    }

    public Flux<MarketTickEvent> streamFor(String symbol) {
        return sink.asFlux().filter(tick -> tick.getSymbol().equalsIgnoreCase(symbol));
    }
}
