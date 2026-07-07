package com.shvmpk.redirect_service.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VisitorUtils {

    private static final String VISITOR_COOKIE = "uid";
    private static final Duration VISITOR_COOKIE_AGE = Duration.ofDays(30);
    private static final String VISITOR_REDIS_PREFIX = "visitors:";

    private final StringRedisTemplate stringRedisTemplate;

    public String getOrCreateVisitorId(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (VISITOR_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        String visitorId = UUID.randomUUID().toString();
        Cookie cookie = new Cookie(VISITOR_COOKIE, visitorId);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) VISITOR_COOKIE_AGE.toSeconds());
        response.addCookie(cookie);
        return visitorId;
    }

    public boolean isNewVisitor(String shortCode, String hashedVisitorId) {
        String key = VISITOR_REDIS_PREFIX + shortCode;
        Long added = stringRedisTemplate.opsForSet().add(key, hashedVisitorId);
        if (added != null && added == 1) {
            stringRedisTemplate.expire(key, VISITOR_COOKIE_AGE);
            return true;
        }
        return false;
    }
}
