package com.shvmpk.shortener_service.exception;

import org.springframework.http.HttpStatus;

public class UrlNotFoundException extends BaseApiException {
    public UrlNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
