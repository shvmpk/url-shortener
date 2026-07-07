package com.shvmpk.shortener_service.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisIdGenerator implements IdGenerator {

    private static final String REDIS_KEY = "id:short-code";
    private static final long INITIAL_VALUE = 100_000_000_000L;
    private static final int BATCH_SIZE = 1000;

    private final RedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private long currentId;
    private long boundary;

    public RedisIdGenerator(RedisTemplate<String, String> redisTemplate,
                            JdbcTemplate jdbcTemplate) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        initializeCounter();
    }

    private void initializeCounter() {
        Long checkpoint = jdbcTemplate.queryForObject(
                "SELECT last_value FROM id_checkpoint", Long.class);
        long seed = Math.max(checkpoint != null ? checkpoint : 0, INITIAL_VALUE);
        redisTemplate.opsForValue().setIfAbsent(REDIS_KEY, String.valueOf(seed));
    }

    @Override
    public synchronized long nextId() {
        if (currentId >= boundary) {
            Long next = redisTemplate.opsForValue().increment(REDIS_KEY, BATCH_SIZE);
            if (next == null) {
                throw new RuntimeException("Failed to generate ID from Redis");
            }
            jdbcTemplate.update(
                    "UPDATE id_checkpoint SET last_value = ? WHERE last_value < ?",
                    next, next);
            currentId = next - BATCH_SIZE;
            boundary = next;
        }
        return IdObfuscator.obfuscate(currentId++);
    }
}
