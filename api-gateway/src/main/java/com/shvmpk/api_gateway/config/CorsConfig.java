package com.shvmpk.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${SHORTENER_SERVICE_URL:http://localhost:8081}")
    private String shortenerServiceUrl;

    @Value("${REDIRECT_SERVICE_URL:http://localhost:8082}")
    private String redirectServiceUrl;

    @Value("${ANALYTICS_SERVICE_URL:http://localhost:8083}")
    private String analyticsServiceUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("shortener-service", r -> r
                .path("/api/v1/urls/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("shortenerCircuitBreaker").setFallbackUri("forward:/fallback/shortener"))
                    .retry(3))
                .uri(shortenerServiceUrl))
            .route("shortener-service-qr", r -> r
                .path("/api/v1/qr/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("shortenerCircuitBreaker").setFallbackUri("forward:/fallback/shortener"))
                    .retry(3))
                .uri(shortenerServiceUrl))
            .route("analytics-service", r -> r
                .path("/api/v1/analytics/**")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("analyticsCircuitBreaker").setFallbackUri("forward:/fallback/analytics"))
                    .retry(3))
                .uri(analyticsServiceUrl))
            .route("redirect-service-verify", r -> r
                .path("/{segment}/verify")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("redirectCircuitBreaker").setFallbackUri("forward:/fallback/redirect"))
                    .retry(3))
                .uri(redirectServiceUrl))
            .route("redirect-service-redirect", r -> r
                .path("/{segment}")
                .filters(f -> f
                    .circuitBreaker(c -> c.setName("redirectCircuitBreaker").setFallbackUri("forward:/fallback/redirect"))
                    .retry(3))
                .uri(redirectServiceUrl))
            .build();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        var corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(List.of("*"));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
