package com.shvmpk.analytics_service.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Converter
public class JsonListInstantConverter implements AttributeConverter<List<Instant>, String> {

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String convertToDatabaseColumn(List<Instant> list) {
        if (list == null) return null;
        try {
            return mapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Instant> convertToEntityAttribute(String json) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, new TypeReference<List<Instant>>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
