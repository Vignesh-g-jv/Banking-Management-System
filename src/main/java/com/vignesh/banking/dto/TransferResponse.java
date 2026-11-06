package com.vignesh.banking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        String reference,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter,
        LocalDateTime createdAt) {
}
