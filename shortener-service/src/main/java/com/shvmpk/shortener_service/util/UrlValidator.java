package com.shvmpk.shortener_service.util;

public interface UrlValidator {
    boolean isValid(String url);
    boolean isReachable(String url);
    String normalize(String url);
}
