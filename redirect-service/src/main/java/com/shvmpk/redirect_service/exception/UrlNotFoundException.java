package com.shvmpk.redirect_service.exception;

public class UrlNotFoundException extends RuntimeException {
    public UrlNotFoundException(String code) {
        super("URL not found: " + code);
    }
}
