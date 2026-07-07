package com.shvmpk.shortener_service.util;

public class Base62Encoder {
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private Base62Encoder() {}

    public static String encode(long num) {
        if (num == 0) return String.valueOf(CHARS.charAt(0));
        StringBuilder sb = new StringBuilder();
        while (num != 0) {
            sb.append(CHARS.charAt((int) Long.remainderUnsigned(num, 62)));
            num = Long.divideUnsigned(num, 62);
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        long result = 0;
        for (int i = 0; i < code.length(); i++) {
            int idx = CHARS.indexOf(code.charAt(i));
            if (idx == -1) {
                throw new IllegalArgumentException("Invalid character: " + code.charAt(i));
            }
            result = result * 62 + idx;
        }
        return result;
    }
}
