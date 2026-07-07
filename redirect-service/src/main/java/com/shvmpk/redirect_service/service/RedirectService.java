package com.shvmpk.redirect_service.service;

import tools.jackson.databind.ObjectMapper;
import com.shvmpk.redirect_service.exception.MaxClicksExceededException;
import com.shvmpk.redirect_service.exception.UrlExpiredException;
import com.shvmpk.redirect_service.exception.UrlNotFoundException;
import com.shvmpk.redirect_service.model.ShortCode;
import com.shvmpk.redirect_service.repository.UrlRepository;
import com.shvmpk.redirect_service.util.VisitorUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedirectService {

    private static final String REDIRECT_CACHE_PREFIX = "redirect:";
    private static final String SHORT_KEY_CACHE_PREFIX = "shortKey:";

    private final UrlRepository urlRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final BloomCacheService bloomCacheService;
    private final AnalyticsService analyticsService;
    private final VisitorUtils visitorUtils;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Retry(name = "redis-cache", fallbackMethod = "resolveShortCodeFallback")
    @CircuitBreaker(name = "redirect-db", fallbackMethod = "resolveShortCodeFallback")
    public ShortCode resolveShortCodeOrAlias(String shortCodeOrAlias) {
        String redisShortKey = SHORT_KEY_CACHE_PREFIX + shortCodeOrAlias;

        String cachedJson = stringRedisTemplate.opsForValue().get(redisShortKey);
        if (cachedJson != null) {
            return parseShortCodeFromJson(cachedJson);
        }

        // Cache stampede protection: serialize concurrent DB lookups
        String mutexKey = "lock:" + redisShortKey;
        boolean locked = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(mutexKey, "1", Duration.ofSeconds(2)));

        if (locked) {
            try {
                String recheck = stringRedisTemplate.opsForValue().get(redisShortKey);
                if (recheck != null) {
                    return parseShortCodeFromJson(recheck);
                }
                return lookupAndCache(shortCodeOrAlias, redisShortKey);
            } finally {
                stringRedisTemplate.delete(mutexKey);
            }
        }

        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String waiter = stringRedisTemplate.opsForValue().get(redisShortKey);
            if (waiter != null) {
                return parseShortCodeFromJson(waiter);
            }
        }

        return lookupAndCache(shortCodeOrAlias, redisShortKey);
    }

    private ShortCode lookupAndCache(String shortCodeOrAlias, String redisShortKey) {
        Optional<ShortCode> byCode = urlRepository.findByShortCodeIgnoreCase(shortCodeOrAlias);
        if (byCode.isPresent()) {
            cacheShortCode(redisShortKey, byCode.get());
            return byCode.get();
        }

        Optional<ShortCode> byAlias = urlRepository.findByAliasIgnoreCase(shortCodeOrAlias);
        if (byAlias.isPresent()) {
            cacheShortCode(SHORT_KEY_CACHE_PREFIX + byAlias.get().getShortCode(), byAlias.get());
            return byAlias.get();
        }

        throw new UrlNotFoundException(shortCodeOrAlias);
    }

    @CircuitBreaker(name = "redirect-db", fallbackMethod = "processRedirectFallback")
    public String processRedirect(ShortCode mapping, HttpServletRequest request, HttpServletResponse response) {

        if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(LocalDateTime.now())) {
            evictCache(mapping.getShortCode());
            throw new UrlExpiredException(mapping.getShortCode());
        }

        if (mapping.getMaxClicks() != null && mapping.getUniqueVisitorCount() >= mapping.getMaxClicks()) {
            throw new MaxClicksExceededException(mapping.getShortCode());
        }

        String redirectCacheKey = REDIRECT_CACHE_PREFIX + mapping.getShortCode();
        String cachedUrl = stringRedisTemplate.opsForValue().get(redirectCacheKey);

        if (cachedUrl == null) {
            cachedUrl = mapping.getOriginalUrl();
            if (mapping.getExpiresAt() != null) {
                Duration ttl = Duration.between(LocalDateTime.now(), mapping.getExpiresAt());
                if (!ttl.isNegative()) {
                    stringRedisTemplate.opsForValue().set(redirectCacheKey, cachedUrl, ttl.toSeconds(), TimeUnit.SECONDS);
                }
            } else {
                stringRedisTemplate.opsForValue().set(redirectCacheKey, cachedUrl, 7, TimeUnit.DAYS);
            }
        }

        String visitorId = visitorUtils.getOrCreateVisitorId(request, response);
        String hashedSignature = hashVisitorId(visitorId);
        boolean isNewVisitor = visitorUtils.isNewVisitor(mapping.getShortCode(), hashedSignature);
        if (isNewVisitor) {
            mapping.setUniqueVisitorCount(mapping.getUniqueVisitorCount() + 1);
            urlRepository.save(mapping);
        }

        String ip = resolveClientIp(request);
        analyticsService.saveAnalyticsAsync(mapping, ip, request);

        meterRegistry.counter("redirect.total",
                "shortCode", mapping.getShortCode()).increment();
        return cachedUrl;
    }

    private void evictCache(String shortCode) {
        stringRedisTemplate.delete(SHORT_KEY_CACHE_PREFIX + shortCode);
        stringRedisTemplate.delete(REDIRECT_CACHE_PREFIX + shortCode);
    }

    private void cacheShortCode(String key, ShortCode shortCode) {
        try {
            String json = objectMapper.writeValueAsString(shortCode);
            stringRedisTemplate.opsForValue().set(key, json, 1, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to serialize ShortCode for caching", e);
        }
    }

    private ShortCode parseShortCodeFromJson(String json) {
        try {
            return objectMapper.readValue(json, ShortCode.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize ShortCode from JSON", e);
            return null;
        }
    }

    private String hashVisitorId(String visitorId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(visitorId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return visitorId;
        }
    }

    private ShortCode resolveShortCodeFallback(String shortCodeOrAlias, Throwable t) {
        log.warn("Circuit breaker fallback for resolveShortCodeOrAlias({}): {}", shortCodeOrAlias, t.getMessage());
        throw new UrlNotFoundException(shortCodeOrAlias);
    }

    private String processRedirectFallback(ShortCode mapping, HttpServletRequest request, HttpServletResponse response, Throwable t) {
        log.warn("Circuit breaker fallback for processRedirect({}): {}", mapping.getShortCode(), t.getMessage());
        throw new RuntimeException("Service temporarily unavailable. Please try again later.");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
