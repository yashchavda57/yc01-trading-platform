package com.chavd.yc01.walletservice.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositRequest(@Positive BigDecimal amount) {
}
