package com.vignesh.banking.exception;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends BankingException {

    public AccountNotFoundException(String accountNumber) {
        super(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "No account found with number " + accountNumber);
    }
}
