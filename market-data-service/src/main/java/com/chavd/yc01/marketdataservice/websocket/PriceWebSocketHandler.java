package com.chavd.yc01.marketdataservice.websocket;

import com.chavd.yc01.marketdataservice.service.PriceStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.http.server.PathContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceWebSocketHandler implements WebSocketHandler {

    private static final PathPattern SYMBOL_PATTERN =
            PathPatternParser.defaultInstance.parse("/ws/prices/{symbol}");

    private final PriceStreamService priceStreamService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String path = session.getHandshakeInfo().getUri().getPath();
        PathContainer pathContainer = PathContainer.parsePath(path);
        PathPattern.PathMatchInfo match = SYMBOL_PATTERN.matchAndExtract(pathContainer);

        if (match == null) {
            log.warn("Rejecting WebSocket handshake, path did not match {}: {}", SYMBOL_PATTERN, path);
            return session.close();
        }

        String symbol = match.getUriVariables().get("symbol").toUpperCase();

        Flux<WebSocketMessage> outbound = priceStreamService.streamFor(symbol)
                .map(this::toJson)
                .map(session::textMessage);

        return session.send(outbound);
    }

    private String toJson(Object tick) {
        try {
            return objectMapper.writeValueAsString(tick);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize tick for WebSocket push", e);
        }
    }
}
