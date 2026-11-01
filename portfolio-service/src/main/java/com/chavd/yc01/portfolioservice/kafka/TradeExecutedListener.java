package com.chavd.yc01.portfolioservice.kafka;

import com.chavd.yc01.common.dto.event.TradeExecutedEvent;
import com.chavd.yc01.portfolioservice.service.PortfolioWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeExecutedListener {

    private final PortfolioWriteService portfolioWriteService;

    @KafkaListener(topics = "trade.executed", containerFactory = "tradeExecutedListenerFactory")
    public void onTradeExecuted(TradeExecutedEvent event) {
        portfolioWriteService.applyTrade(event);
    }
}
