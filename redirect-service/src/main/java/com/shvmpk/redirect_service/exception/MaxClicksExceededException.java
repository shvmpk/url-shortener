package com.shvmpk.redirect_service.exception;

public class MaxClicksExceededException extends RuntimeException {
    public MaxClicksExceededException(String code) {
        super("Maximum click limit reached: " + code);
    }
}
