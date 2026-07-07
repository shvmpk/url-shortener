package com.shvmpk.shortener_service.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.opentelemetry.api.trace.Span;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String traceId,
        String requestId
) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path,
                Span.current().getSpanContext().getTraceId(),
                null);
    }
}
