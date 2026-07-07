package com.shvmpk.shortener_service.exception;

import org.springframework.http.HttpStatus;

public class InvalidExpirationException extends BaseApiException {
    public InvalidExpirationException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
