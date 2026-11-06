package com.vignesh.banking.dto;

import com.vignesh.banking.model.Account;
import com.vignesh.banking.model.AccountStatus;
import com.vignesh.banking.model.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        String accountNumber,
        String holderName,
        String email,
        AccountType accountType,
        BigDecimal balance,
        AccountStatus status,
        LocalDateTime createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountNumber(),
                account.getHolderName(),
                account.getEmail(),
                account.getAccountType(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt());
    }
}
