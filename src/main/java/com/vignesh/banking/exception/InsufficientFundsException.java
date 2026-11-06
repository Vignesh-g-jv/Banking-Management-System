package com.vignesh.banking.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class InsufficientFundsException extends BankingException {

    public InsufficientFundsException(String accountNumber, BigDecimal balance, BigDecimal requested) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS",
                "Account " + accountNumber + " has " + balance.toPlainString()
                        + " available, which is less than the requested " + requested.toPlainString());
    }
}
