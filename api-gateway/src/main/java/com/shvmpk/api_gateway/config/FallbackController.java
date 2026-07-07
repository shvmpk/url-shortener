package com.shvmpk.api_gateway.config;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Hidden
@Tag(name = "Fallback", description = "Circuit breaker fallback endpoints (internal)")
public class FallbackController {

    @RequestMapping("/fallback/shortener")
    public ResponseEntity<Map<String, Object>> shortenerFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "down", "service", "shortener-service",
                        "message", "Shortener service is temporarily unavailable"));
    }

    @RequestMapping("/fallback/redirect")
    public ResponseEntity<Map<String, Object>> redirectFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "down", "service", "redirect-service",
                        "message", "Redirect service is temporarily unavailable"));
    }

    @RequestMapping("/fallback/analytics")
    public ResponseEntity<Map<String, Object>> analyticsFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "down", "service", "analytics-service",
                        "message", "Analytics service is temporarily unavailable"));
    }
}
