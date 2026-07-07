package com.shvmpk.shortener_service.util;

public interface PasswordService {
    String hash(String plainPassword);
    boolean verify(String plainPassword, String hashedPassword);
    String generateRandom(int length);
}
