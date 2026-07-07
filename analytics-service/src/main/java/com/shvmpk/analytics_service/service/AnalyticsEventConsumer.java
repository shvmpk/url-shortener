package com.shvmpk.analytics_service.service;

import com.shvmpk.analytics_service.dto.AnalyticsEvent;
import com.shvmpk.analytics_service.model.Analytics;
import com.shvmpk.analytics_service.repository.AnalyticsRepository;
import com.shvmpk.analytics_service.service.GeoIpService.GeoIpResult;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumer {

    private final AnalyticsRepository analyticsRepository;
    private final GeoIpService geoIpService;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "analytics-events", groupId = "analytics-group")
    @Transactional
    public void consume(List<AnalyticsEvent> events) {
        if (events == null || events.isEmpty()) return;

        log.debug("Processing batch of {} analytics events", events.size());
        DistributionSummary.builder("analytics.batch.size")
                .tag("topic", "analytics-events")
                .register(meterRegistry).record(events.size());

        Map<ShortCodeDateKey, List<AnalyticsEvent>> grouped = events.stream()
                .collect(Collectors.groupingBy(
                        e -> new ShortCodeDateKey(e.getShortCode(), e.getAccessDate()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<Analytics> merged = new ArrayList<>(grouped.size());

        for (Map.Entry<ShortCodeDateKey, List<AnalyticsEvent>> entry : grouped.entrySet()) {
            List<AnalyticsEvent> group = entry.getValue();
            ShortCodeDateKey key = entry.getKey();

            try {
                Analytics analytics = mergeGroup(key.shortCode, key.accessDate, group);
                if (analytics != null) {
                    merged.add(analytics);
                }
            } catch (Exception e) {
                log.error("Failed to merge analytics for shortCode={} date={}: {}",
                        key.shortCode, key.accessDate, e.getMessage());
            }
        }

        if (!merged.isEmpty()) {
            Timer.Sample sample = Timer.start(meterRegistry);
            analyticsRepository.saveAll(merged);
            sample.stop(meterRegistry.timer("analytics.db.save.duration", "table", "analytics"));
            meterRegistry.counter("analytics.events.processed",
                    "topic", "analytics-events").increment(events.size());
            log.debug("Batch saved {} analytics records (from {} events)", merged.size(), events.size());
        }
    }

    private Analytics mergeGroup(String shortCode, LocalDate accessDate, List<AnalyticsEvent> events) {
        Optional<Analytics> optional = analyticsRepository
                .findByShortCodeAndAccessDate(shortCode, accessDate);

        Analytics analytics = optional.orElseGet(() -> Analytics.builder()
                .shortCode(shortCode)
                .accessDate(accessDate)
                .totalVisitCount(0)
                .browserVisitCounts(new HashMap<>())
                .deviceTypeVisitCounts(new HashMap<>())
                .osVisitCounts(new HashMap<>())
                .browserLastSeen(new HashMap<>())
                .deviceLastSeen(new HashMap<>())
                .recentAccessTimes(new ArrayList<>())
                .clicksLast10Min(0)
                .clicksLast1Hour(0)
                .build());

        for (AnalyticsEvent event : events) {
            mergeEvent(analytics, event);
        }

        analytics.setClicksLast10Min((int) analytics.getRecentAccessTimes().stream()
                .filter(ts -> ts.isAfter(Instant.now().minus(Duration.ofMinutes(10))))
                .count());
        analytics.setClicksLast1Hour(analytics.getRecentAccessTimes().size());

        return analytics;
    }

    private void mergeEvent(Analytics analytics, AnalyticsEvent event) {
        mergeMapCount(ensureMap(analytics.getBrowserVisitCounts()), event.getBrowserVisitCounts());
        mergeMapCount(ensureMap(analytics.getDeviceTypeVisitCounts()), event.getDeviceTypeVisitCounts());
        mergeMapCount(ensureMap(analytics.getOsVisitCounts()), event.getOsVisitCounts());

        mergeLastSeen(ensureMap(analytics.getBrowserLastSeen()), event.getBrowserLastSeen());
        mergeLastSeen(ensureMap(analytics.getDeviceLastSeen()), event.getDeviceLastSeen());

        analytics.setTotalVisitCount(
                Optional.ofNullable(analytics.getTotalVisitCount()).orElse(0)
                + Optional.ofNullable(event.getTotalVisitCount()).orElse(1));

        Instant now = Instant.now();
        List<Instant> recent = analytics.getRecentAccessTimes();
        if (recent == null) recent = new ArrayList<>();
        recent.add(now);
        recent.removeIf(ts -> ts.isBefore(now.minus(Duration.ofHours(1))));
        analytics.setRecentAccessTimes(recent);

        if (event.getIp() != null && !event.getIp().isBlank()) {
            GeoIpResult geo = geoIpService.lookup(event.getIp());
            if (geo.country() != null) analytics.setCountry(geo.country());
            if (geo.city() != null) analytics.setCity(geo.city());
            if (geo.region() != null) analytics.setRegion(geo.region());
            if (geo.continent() != null) analytics.setContinent(geo.continent());
            if (geo.latitude() != null) analytics.setLatitude(geo.latitude());
            if (geo.longitude() != null) analytics.setLongitude(geo.longitude());
            if (geo.asn() != null) analytics.setAsn(geo.asn());
            if (geo.isp() != null) analytics.setIsp(geo.isp());
            if (geo.organization() != null) analytics.setOrganization(geo.organization());
        }

        if (event.getReferer() != null) analytics.setReferer(event.getReferer());
        if (event.getUtmSource() != null) analytics.setUtmSource(event.getUtmSource());
        if (event.getUtmMedium() != null) analytics.setUtmMedium(event.getUtmMedium());
        if (event.getUtmCampaign() != null) analytics.setUtmCampaign(event.getUtmCampaign());
        if (event.getUtmTerm() != null) analytics.setUtmTerm(event.getUtmTerm());
        if (event.getIsBot() != null) analytics.setIsBot(event.getIsBot());
        if (event.getUserAgent() != null) analytics.setUserAgent(event.getUserAgent());
        if (event.getLastAccessTime() != null) analytics.setLastAccessTime(event.getLastAccessTime());
    }

    private record ShortCodeDateKey(String shortCode, LocalDate accessDate) {}

    private <K, V> Map<K, V> ensureMap(Map<K, V> map) {
        return map != null ? map : new HashMap<>();
    }

    private void mergeMapCount(Map<String, Integer> existing, Map<String, Integer> incoming) {
        if (incoming == null) return;
        incoming.forEach((key, value) ->
                existing.merge(key, value, Integer::sum));
    }

    private void mergeLastSeen(Map<String, Instant> existing, Map<String, Instant> incoming) {
        if (incoming == null) return;
        incoming.forEach((key, value) ->
                existing.merge(key, value, (oldVal, newVal) ->
                        newVal.isAfter(oldVal) ? newVal : oldVal));
    }
}
