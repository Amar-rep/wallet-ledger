package com.walletledger.walletbackend.exception;

import org.springframework.http.HttpStatus;

/** An expected domain-rule failure that can be returned directly to an API client. */
public class BusinessRuleException extends RuntimeException {

    private final HttpStatus status;

    public BusinessRuleException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
