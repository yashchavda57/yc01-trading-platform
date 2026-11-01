package com.chavd.yc01.portfolioservice.controller;

import com.chavd.yc01.common.dto.ApiResponse;
import com.chavd.yc01.portfolioservice.dto.HoldingResponse;
import com.chavd.yc01.portfolioservice.dto.PnlResponse;
import com.chavd.yc01.portfolioservice.service.PortfolioReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Both endpoints only ever read through PortfolioReadService -- nothing here writes; writes happen only off trade.executed. */
@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioReadService portfolioReadService;

    @GetMapping("/{userId}/holdings")
    public ApiResponse<List<HoldingResponse>> getHoldings(@PathVariable UUID userId) {
        return ApiResponse.ok(portfolioReadService.getHoldings(userId));
    }

    @GetMapping("/{userId}/pnl")
    public ApiResponse<PnlResponse> getPnl(@PathVariable UUID userId) {
        return ApiResponse.ok(portfolioReadService.getPnl(userId));
    }
}
