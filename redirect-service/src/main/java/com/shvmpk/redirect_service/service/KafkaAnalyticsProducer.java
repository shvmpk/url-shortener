package com.shvmpk.redirect_service.service;

import com.shvmpk.redirect_service.dto.AnalyticsEvent;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaAnalyticsProducer {

    private final KafkaTemplate<String, AnalyticsEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Retry(name = "kafka-producer", fallbackMethod = "sendFallback")
    public void sendAnalyticsEvent(AnalyticsEvent event) {
        kafkaTemplate.send("analytics-events", event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        meterRegistry.counter("kafka.producer.error",
                                "topic", "analytics-events").increment();
                        log.error("Failed to send analytics event: {}", ex.getMessage());
                    }
                });
    }

    private void sendFallback(AnalyticsEvent event, Throwable t) {
        meterRegistry.counter("kafka.producer.fallback",
                "topic", "analytics-events").increment();
        log.error("Failed to send analytics event after retries: {}", t.getMessage());
    }
}
