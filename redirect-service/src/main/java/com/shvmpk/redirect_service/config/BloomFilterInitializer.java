package com.shvmpk.redirect_service.config;

import com.shvmpk.redirect_service.model.ShortCode;
import com.shvmpk.redirect_service.repository.UrlRepository;
import com.shvmpk.redirect_service.service.BloomCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class BloomFilterInitializer implements CommandLineRunner {

    private final UrlRepository urlRepository;
    private final BloomCacheService bloomCacheService;

    @Override
    public void run(String... args) {
        try {
            Set<String> existingCodes = new HashSet<>();
            for (ShortCode sc : urlRepository.findAll()) {
                existingCodes.add(sc.getShortCode());
                if (sc.getAlias() != null) {
                    existingCodes.add(sc.getAlias().toLowerCase());
                }
            }
            bloomCacheService.initializeBloomFilter(existingCodes);
            log.info("Bloom filter initialized with {} entries from database", existingCodes.size());
        } catch (Exception e) {
            log.warn("Failed to initialize Bloom filter from database: {}", e.getMessage());
        }
    }
}
