package com.shvmpk.analytics_service.repository;

import com.shvmpk.analytics_service.model.Analytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalyticsRepository extends JpaRepository<Analytics, Long> {
    List<Analytics> findByShortCodeIgnoreCase(String shortCode);
    Optional<Analytics> findByShortCodeAndAccessDate(String shortCode, LocalDate accessDate);
}
