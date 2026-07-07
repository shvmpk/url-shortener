package com.shvmpk.shortener_service.util;

import org.springframework.stereotype.Component;

@Component
public class ShortUrlGenerator {

    private final IdGenerator idGenerator;

    public ShortUrlGenerator(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    public String generate() {
        return Base62Encoder.encode(idGenerator.nextId());
    }
}
