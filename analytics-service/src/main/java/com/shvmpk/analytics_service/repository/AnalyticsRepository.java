package com.shvmpk.analytics_service.repository;

import com.shvmpk.analytics_service.model.Analytics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalyticsRepository extends JpaRepository<Analytics, Long> {
    List<Analytics> findByShortCodeIgnoreCase(String shortCode);

    Optional<Analytics> findByShortCodeAndAccessDate(String shortCode, LocalDate accessDate);

    Page<Analytics> findByShortCodeIgnoreCase(String shortCode, Pageable pageable);

    @Query("""
            SELECT a FROM Analytics a
            WHERE LOWER(a.shortCode) = LOWER(:shortCode)
            AND (:from IS NULL OR a.accessDate >= :from)
            AND (:to IS NULL OR a.accessDate <= :to)
            """)
    Page<Analytics> findByShortCodeWithDateRange(
            @Param("shortCode") String shortCode,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);
}
