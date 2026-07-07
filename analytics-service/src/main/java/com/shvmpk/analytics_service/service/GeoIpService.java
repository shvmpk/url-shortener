package com.shvmpk.analytics_service.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeoIpService {

    @Value("${geoip.database.city-path:classpath:geoip/GeoLite2-City.mmdb}")
    private String cityDatabasePath;

    @Value("${geoip.database.asn-path:classpath:geoip/GeoLite2-ASN.mmdb}")
    private String asnDatabasePath;

    private final ResourceLoader resourceLoader;

    private DatabaseReader cityReader;
    private DatabaseReader asnReader;
    private boolean available = false;

    @PostConstruct
    public void init() {
        cityReader = initReader(cityDatabasePath, "City");
        asnReader = initReader(asnDatabasePath, "ASN");
        available = (cityReader != null);
    }

    private DatabaseReader initReader(String path, String label) {
        try {
            InputStream in = resourceLoader.getResource(path).getInputStream();
            DatabaseReader reader = new DatabaseReader.Builder(in).build();
            log.info("GeoIP {} database loaded from: {}", label, path);
            return reader;
        } catch (IOException e) {
            log.warn("Failed to load GeoIP {} database from {}: {}", label, path, e.getMessage());
            return null;
        }
    }

    @CircuitBreaker(name = "analytics-geoip", fallbackMethod = "lookupFallback")
    public GeoIpResult lookup(String ip) {
        if (!available || ip == null || ip.isBlank()) {
            return new GeoIpResult(null, null, null, null, null, null, null, null, null);
        }

        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            if (ipAddress.isSiteLocalAddress() || ipAddress.isLoopbackAddress()) {
                return new GeoIpResult(null, null, null, null, null, null, null, null, null);
            }

            String country = null, city = null, region = null, continent = null;
            Double latitude = null, longitude = null;
            Long asn = null;
            String isp = null, organization = null;

            if (cityReader != null) {
                CityResponse response = cityReader.city(ipAddress);
                country = Optional.ofNullable(response.getCountry()).map(c -> c.getName()).orElse(null);
                city = Optional.ofNullable(response.getCity()).map(c -> c.getName()).orElse(null);
                region = Optional.ofNullable(response.getMostSpecificSubdivision()).map(s -> s.getName()).orElse(null);
                continent = Optional.ofNullable(response.getContinent()).map(c -> c.getName()).orElse(null);
                if (response.getLocation() != null) {
                    latitude = response.getLocation().getLatitude();
                    longitude = response.getLocation().getLongitude();
                }
            }

            if (asnReader != null) {
                AsnResponse asnResponse = asnReader.asn(ipAddress);
                asn = asnResponse.getAutonomousSystemNumber();
                isp = asnResponse.getAutonomousSystemOrganization();
                organization = asnResponse.getAutonomousSystemOrganization();
            }

            return new GeoIpResult(country, city, region, continent, latitude, longitude, asn, isp, organization);
        } catch (Exception e) {
            log.debug("GeoIP lookup failed for IP {}: {}", ip, e.getMessage());
            return new GeoIpResult(null, null, null, null, null, null, null, null, null);
        }
    }

    private GeoIpResult lookupFallback(String ip, Throwable t) {
        log.debug("Circuit breaker fallback for GeoIP lookup: {}", t.getMessage());
        return new GeoIpResult(null, null, null, null, null, null, null, null, null);
    }

    public record GeoIpResult(String country, String city, String region, String continent,
                              Double latitude, Double longitude,
                              Long asn, String isp, String organization) {}
}
