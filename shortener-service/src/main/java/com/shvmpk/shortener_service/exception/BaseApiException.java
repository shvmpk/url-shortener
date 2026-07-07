package com.shvmpk.shortener_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class BaseApiException extends RuntimeException {
    private final HttpStatus status;

    public BaseApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
