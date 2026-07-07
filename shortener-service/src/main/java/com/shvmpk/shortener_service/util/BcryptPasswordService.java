package com.shvmpk.shortener_service.util;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class BcryptPasswordService implements PasswordService {
    private static final int COST = 12;

    @Override
    public String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) return null;
        return BCrypt.withDefaults().hashToString(COST, plainPassword.toCharArray());
    }

    @Override
    public boolean verify(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) return false;
        return BCrypt.verifyer().verify(plainPassword.toCharArray(), hashedPassword).verified;
    }

    @Override
    public String generateRandom(int length) {
        if (length <= 0) return "";
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+{}[]|:;<>,.?/~`-=";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(characters.length());
            password.append(characters.charAt(index));
        }
        return password.toString();
    }
}
