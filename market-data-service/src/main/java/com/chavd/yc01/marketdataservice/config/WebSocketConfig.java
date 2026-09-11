package com.chavd.yc01.marketdataservice.config;

import com.chavd.yc01.marketdataservice.websocket.PriceWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

/**
 * Registers a plain WebFlux WebSocketHandler at /ws/prices/{symbol} without a
 * @Controller — WebSocket endpoints in WebFlux don't support @PathVariable, so
 * the symbol is pulled out of the handshake URI manually inside the handler
 * (see PriceWebSocketHandler).
 */
@Configuration
public class WebSocketConfig {

    private static final String PRICE_STREAM_PATH = "/ws/prices/{symbol}";

    @Bean
    public HandlerMapping priceWebSocketHandlerMapping(PriceWebSocketHandler priceWebSocketHandler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of(PRICE_STREAM_PATH, (WebSocketHandler) priceWebSocketHandler));
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
