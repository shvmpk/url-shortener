package com.shvmpk.shortener_service.exception;

import org.springframework.http.HttpStatus;

public class UrlConflictException extends BaseApiException {
    public UrlConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
