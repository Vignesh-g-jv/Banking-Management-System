package com.vignesh.banking.exception;

import org.springframework.http.HttpStatus;

public class AccountStateException extends BankingException {

    public AccountStateException(String message) {
        super(HttpStatus.CONFLICT, "ACCOUNT_STATE_CONFLICT", message);
    }
}
