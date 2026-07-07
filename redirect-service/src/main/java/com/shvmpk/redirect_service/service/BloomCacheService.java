package com.shvmpk.redirect_service.service;

import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.IntegerOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class BloomCacheService {

    private static final String BLOOM_FILTER_KEY = "bloom:shortcodes";
    private static final double FALSE_POSITIVE_RATE = 0.01;
    private static final long EXPECTED_ENTRIES = 10_000_000L;

    private static final ProtocolKeyword BF_RESERVE = kw("BF.RESERVE");
    private static final ProtocolKeyword BF_ADD = kw("BF.ADD");
    private static final ProtocolKeyword BF_EXISTS = kw("BF.EXISTS");

    private final StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void init() {
        try (var conn = (LettuceConnection) stringRedisTemplate.getConnectionFactory().getConnection()) {
            var args = new CommandArgs<>(StringCodec.UTF8)
                    .addKey(BLOOM_FILTER_KEY)
                    .addValue(String.valueOf(FALSE_POSITIVE_RATE))
                    .addValue(String.valueOf(EXPECTED_ENTRIES));
            sync(conn).dispatch(BF_RESERVE, new StatusOutput<>(StringCodec.UTF8), args);
            log.info("Bloom filter '{}' reserved (1% fp, 10M capacity)", BLOOM_FILTER_KEY);
        } catch (Exception e) {
            log.debug("Bloom filter already exists: {}", e.getMessage());
        }
    }

    public void initializeBloomFilter(Set<String> existingCodes) {
        log.info("Initializing Bloom filter with {} entries", existingCodes.size());
        if (existingCodes.isEmpty()) return;

        try (var conn = (LettuceConnection) stringRedisTemplate.getConnectionFactory().getConnection()) {
            var commands = sync(conn);
            for (String code : existingCodes) {
                var args = new CommandArgs<>(StringCodec.UTF8)
                        .addKey(BLOOM_FILTER_KEY)
                        .addValue(code);
                commands.dispatch(BF_ADD, new StatusOutput<>(StringCodec.UTF8), args);
            }
        }
        log.info("Bloom filter initialized with {} entries", existingCodes.size());
    }

    public boolean mightContain(String code) {
        try (var conn = (LettuceConnection) stringRedisTemplate.getConnectionFactory().getConnection()) {
            var args = new CommandArgs<>(StringCodec.UTF8)
                    .addKey(BLOOM_FILTER_KEY)
                    .addValue(code);
            Long result = sync(conn).dispatch(BF_EXISTS, new IntegerOutput<>(StringCodec.UTF8), args);
            return result != null && result == 1L;
        } catch (Exception e) {
            log.warn("Bloom filter check failed, falling back to true: {}", e.getMessage());
        }
        return true;
    }

    public void add(String code) {
        try (var conn = (LettuceConnection) stringRedisTemplate.getConnectionFactory().getConnection()) {
            var args = new CommandArgs<>(StringCodec.UTF8)
                    .addKey(BLOOM_FILTER_KEY)
                    .addValue(code);
            sync(conn).dispatch(BF_ADD, new StatusOutput<>(StringCodec.UTF8), args);
        }
    }

    @SuppressWarnings("unchecked")
    private RedisCommands<String, String> sync(LettuceConnection conn) {
        return (RedisCommands<String, String>) conn.getNativeConnection();
    }

    private static ProtocolKeyword kw(final String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        return () -> bytes;
    }
}
