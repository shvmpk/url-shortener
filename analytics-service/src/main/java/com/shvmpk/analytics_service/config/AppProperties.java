package com.shvmpk.analytics_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl, GeoIp geoip) {
    public record GeoIp(String databasePath) {}
}
