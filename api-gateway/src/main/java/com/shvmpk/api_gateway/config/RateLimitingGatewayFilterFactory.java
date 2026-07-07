package com.shvmpk.api_gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class RateLimitingGatewayFilterFactory extends
        AbstractGatewayFilterFactory<RateLimitingGatewayFilterFactory.Config> {

    private static final String KEY_PREFIX = "rate_limit:";

    private final StringRedisTemplate redisTemplate;

    public RateLimitingGatewayFilterFactory(StringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String clientIp = "unknown";
            var remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                clientIp = remoteAddress.getAddress().getHostAddress();
            }

            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route != null ? route.getId() : "unknown";
            String key = KEY_PREFIX + routeId + ":" + clientIp;

            Long count = redisTemplate.opsForValue().increment(key);
            if (count == 1) {
                redisTemplate.expire(key, Duration.ofMinutes(config.getRefillPeriodMinutes()));
            }

            if (count <= config.getCapacity()) {
                return chain.filter(exchange);
            }

            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("Retry-After",
                    String.valueOf(config.getRefillPeriodMinutes() * 60));
            log.warn("Rate limit exceeded for route={} clientIp={}", routeId, clientIp);
            return exchange.getResponse().setComplete();
        };
    }

    public static class Config {
        private int capacity = 1000;
        private int refillTokens = 500;
        private int refillPeriodMinutes = 1;

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getRefillTokens() { return refillTokens; }
        public void setRefillTokens(int refillTokens) { this.refillTokens = refillTokens; }
        public int getRefillPeriodMinutes() { return refillPeriodMinutes; }
        public void setRefillPeriodMinutes(int refillPeriodMinutes) { this.refillPeriodMinutes = refillPeriodMinutes; }
    }
}
