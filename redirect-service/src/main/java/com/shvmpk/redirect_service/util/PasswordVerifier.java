package com.shvmpk.redirect_service.util;

public interface PasswordVerifier {
    boolean verify(String plainPassword, String hashedPassword);
}
