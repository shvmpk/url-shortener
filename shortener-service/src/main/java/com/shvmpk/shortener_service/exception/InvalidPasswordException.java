package com.shvmpk.shortener_service.exception;

import org.springframework.http.HttpStatus;

public class InvalidPasswordException extends BaseApiException {
    public InvalidPasswordException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
