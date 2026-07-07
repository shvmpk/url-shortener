package com.shvmpk.analytics_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Table(name = "analytics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"shortCode", "accessDate"}))
public class Analytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String shortCode;

    @Column(nullable = false)
    private LocalDate accessDate;

    private Integer totalVisitCount;

    @Convert(converter = com.shvmpk.analytics_service.util.JsonMapConverter.class)
    private Map<String, Integer> browserVisitCounts;

    @Convert(converter = com.shvmpk.analytics_service.util.JsonMapConverter.class)
    private Map<String, Integer> deviceTypeVisitCounts;

    @Convert(converter = com.shvmpk.analytics_service.util.JsonMapConverter.class)
    private Map<String, Integer> osVisitCounts;

    @Convert(converter = com.shvmpk.analytics_service.util.JsonMapConverter.class)
    private Map<String, Instant> browserLastSeen;

    @Convert(converter = com.shvmpk.analytics_service.util.JsonMapConverter.class)
    private Map<String, Instant> deviceLastSeen;

    private String country;
    private String city;
    private String region;
    private String continent;

    private Double latitude;
    private Double longitude;

    private Long asn;
    private String isp;
    private String organization;

    private String referer;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;
    private String utmTerm;

    private Boolean isBot;
    private String userAgent;

    @Convert(converter = com.shvmpk.analytics_service.util.JsonListInstantConverter.class)
    private List<Instant> recentAccessTimes;

    private Integer clicksLast10Min;
    private Integer clicksLast1Hour;

    private Instant lastAccessTime;

    @Version
    private Long version;
}
