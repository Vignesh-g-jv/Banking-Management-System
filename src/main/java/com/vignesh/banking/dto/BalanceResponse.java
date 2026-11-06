package com.vignesh.banking.dto;

import java.math.BigDecimal;

public record BalanceResponse(String accountNumber, BigDecimal balance) {
}
