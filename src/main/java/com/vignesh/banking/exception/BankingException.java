package com.vignesh.banking.exception;

import org.springframework.http.HttpStatus;

/** Base class for every business-rule failure. Carries the HTTP status and a stable error code. */
public abstract class BankingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected BankingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
