package com.chavd.yc01.walletservice.kafka;

import com.chavd.yc01.common.dto.event.TradeExecutedEvent;
import com.chavd.yc01.walletservice.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class TradeExecutedListener {

    private final WalletService walletService;

    @KafkaListener(topics = "trade.executed", containerFactory = "tradeExecutedListenerFactory")
    public void onTradeExecuted(TradeExecutedEvent event) {
        BigDecimal amount = event.getPrice().multiply(BigDecimal.valueOf(event.getQuantity()));
        walletService.settleTrade(event.getTradeId(), event.getOrderId(),
                event.getBuyUserId(), event.getSellUserId(), amount);
    }
}
