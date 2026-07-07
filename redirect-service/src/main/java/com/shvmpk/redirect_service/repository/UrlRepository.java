package com.shvmpk.redirect_service.repository;

import com.shvmpk.redirect_service.model.ShortCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<ShortCode, Long> {
    Optional<ShortCode> findByShortCodeIgnoreCase(String shortCode);
    Optional<ShortCode> findByAliasIgnoreCase(String alias);
}
