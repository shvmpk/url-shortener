package com.shvmpk.redirect_service.service;

import com.shvmpk.redirect_service.dto.AnalyticsEvent;
import com.shvmpk.redirect_service.model.ShortCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final KafkaAnalyticsProducer kafkaAnalyticsProducer;

    private static final UserAgentAnalyzer userAgentAnalyzer = UserAgentAnalyzer
            .newBuilder()
            .withCache(10000)
            .build();

    public void saveAnalyticsAsync(ShortCode mapping, String ip, HttpServletRequest request) {
        try {
            String userAgentHeader = request.getHeader("User-Agent");
            if (userAgentHeader == null) userAgentHeader = "";
            UserAgent agent = userAgentAnalyzer.parse(userAgentHeader);

            String deviceType = agent.getValue("DeviceClass");
            String browser = agent.getValue("AgentName");
            String os = agent.getValue("OperatingSystemName");
            boolean isBot = "Robot".equalsIgnoreCase(deviceType)
                    || "Mobile Robot".equalsIgnoreCase(deviceType);

            String referer = request.getHeader("referer");
            String utmSource = request.getParameter("utm_source");
            String utmMedium = request.getParameter("utm_medium");
            String utmCampaign = request.getParameter("utm_campaign");
            String utmTerm = request.getParameter("utm_term");

            Instant now = Instant.now();
            LocalDate accessDate = LocalDate.now();

            Map<String, Integer> browserVisitCounts = Map.of(browser, 1);
            Map<String, Integer> deviceTypeVisitCounts = Map.of(deviceType, 1);
            Map<String, Integer> osVisitCounts = Map.of(os, 1);

            Map<String, Instant> browserLastSeen = Map.of(browser, now);
            Map<String, Instant> deviceLastSeen = Map.of(deviceType, now);

            AnalyticsEvent event = AnalyticsEvent.builder()
                    .shortCode(mapping.getShortCode())
                    .accessDate(accessDate)
                    .totalVisitCount(1)
                    .browserVisitCounts(browserVisitCounts)
                    .deviceTypeVisitCounts(deviceTypeVisitCounts)
                    .osVisitCounts(osVisitCounts)
                    .browserLastSeen(browserLastSeen)
                    .deviceLastSeen(deviceLastSeen)
                    .os(os)
                    .deviceType(deviceType)
                    .browser(browser)
                    .ip(ip)
                    .referer(referer)
                    .utmSource(utmSource)
                    .utmMedium(utmMedium)
                    .utmCampaign(utmCampaign)
                    .utmTerm(utmTerm)
                    .isBot(isBot)
                    .userAgent(userAgentHeader)
                    .lastAccessTime(now)
                    .build();

            kafkaAnalyticsProducer.sendAnalyticsEvent(event);
        } catch (Exception e) {
            log.error("Failed to process analytics for shortCode={}: {}", mapping.getShortCode(), e.getMessage());
        }
    }
}
