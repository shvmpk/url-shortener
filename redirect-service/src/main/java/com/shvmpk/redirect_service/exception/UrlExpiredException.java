package com.shvmpk.redirect_service.exception;

public class UrlExpiredException extends RuntimeException {
    public UrlExpiredException(String code) {
        super("URL has expired: " + code);
    }
}
