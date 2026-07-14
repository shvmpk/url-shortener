package com.shvmpk.analytics_service.controller;

import com.shvmpk.analytics_service.model.Analytics;
import com.shvmpk.analytics_service.repository.AnalyticsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "Endpoints for querying analytics data")
public class AnalyticsController {

    private final AnalyticsRepository analyticsRepository;

    @GetMapping
    @Operation(summary = "Fetch analytics by shortCode and optional date range (paginated)")
    public Page<Analytics> getAnalytics(
            @RequestParam String shortCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return analyticsRepository.findByShortCodeWithDateRange(shortCode, from, to, PageRequest.of(page, size));
    }

    @GetMapping("/{shortCode}")
    @Operation(summary = "Fetch all analytics rows for a shortCode")
    public List<Analytics> getAnalyticsByShortCode(@PathVariable String shortCode) {
        return analyticsRepository.findByShortCodeIgnoreCase(shortCode);
    }
}
