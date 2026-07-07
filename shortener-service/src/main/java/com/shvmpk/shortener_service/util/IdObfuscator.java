package com.shvmpk.shortener_service.util;

public class IdObfuscator {

    // Knuth's golden ratio multiplier — odd, so bijective mod 2^64
    public static final long MULTIPLIER = 0x9E3779B97F4A7C15L;

    // Modular inverse of MULTIPLIER mod 2^64 (pre-computed via extended Euclidean algorithm)
    private static final long INVERSE = 0xF1DE83E19937733DL;

    private IdObfuscator() {}

    public static long obfuscate(long id) {
        return id * MULTIPLIER;
    }

    public static long unobfuscate(long obfuscated) {
        return obfuscated * INVERSE;
    }
}
