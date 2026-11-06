package com.vignesh.banking.dto;

import com.vignesh.banking.model.BankTransaction;
import com.vignesh.banking.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        String reference,
        String accountNumber,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        String counterpartyAccount,
        LocalDateTime createdAt) {

    public static TransactionResponse from(BankTransaction tx) {
        return new TransactionResponse(
                tx.getReference(),
                tx.getAccount().getAccountNumber(),
                tx.getType(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getDescription(),
                tx.getCounterpartyAccount(),
                tx.getCreatedAt());
    }
}
