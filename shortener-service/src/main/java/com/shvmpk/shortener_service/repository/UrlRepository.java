package com.shvmpk.shortener_service.repository;

import com.shvmpk.shortener_service.model.ShortCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<ShortCode, Long> {
    boolean existsByShortCodeIgnoreCase(String shortCode);
    boolean existsByAliasIgnoreCase(String alias);
    Optional<ShortCode> findFirstByOriginalUrlIgnoreCase(String originalUrl);
    Optional<ShortCode> findByShortCodeIgnoreCase(String shortCode);
    Optional<ShortCode> findByAliasIgnoreCase(String alias);
}
