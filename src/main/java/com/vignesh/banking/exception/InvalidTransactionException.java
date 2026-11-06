package com.vignesh.banking.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransactionException extends BankingException {

    public InvalidTransactionException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION", message);
    }
}
