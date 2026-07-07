package com.shvmpk.shortener_service.controller;

import com.shvmpk.shortener_service.dto.ShortUrlRequest;
import com.shvmpk.shortener_service.dto.ShortUrlResponse;
import com.shvmpk.shortener_service.dto.ShortUrlUpdateRequest;
import com.shvmpk.shortener_service.dto.ShortUrlUpdateResponse;
import com.shvmpk.shortener_service.model.ShortCode;
import com.shvmpk.shortener_service.service.ShortenService;
import com.shvmpk.shortener_service.util.ShortUrlUpdateValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/urls")
@Validated
@Tag(name = "URL Management", description = "Endpoints for managing short URLs")
public class UrlManagementController {
    private final ShortenService shortenService;

    @Operation(summary = "Create a short URL")
    @PostMapping("/shorten")
    public ResponseEntity<ShortUrlResponse> shortenUrl(@Valid @RequestBody ShortUrlRequest request) {
        ShortUrlResponse response = shortenService.shortenUrl(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update a short URL")
    @PutMapping("/{shortCodeOrAlias}")
    public ResponseEntity<ShortUrlUpdateResponse> updateShortUrl(
            @PathVariable String shortCodeOrAlias,
            @Valid @RequestBody ShortUrlUpdateRequest updatedData
    ) {
        ShortCode mapping = shortenService.resolveShortCodeOrAlias(shortCodeOrAlias);
        ShortUrlUpdateValidator.validate(updatedData);
        ShortUrlUpdateResponse response = shortenService.updateUrl(mapping.getShortCode(), updatedData);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete a short URL")
    @DeleteMapping("/{shortCodeOrAlias}")
    public ResponseEntity<String> deleteShortUrl(@PathVariable String shortCodeOrAlias) {
        ShortCode mapping = shortenService.resolveShortCodeOrAlias(shortCodeOrAlias);
        shortenService.deleteUrl(mapping.getShortCode());
        return ResponseEntity.ok("URL deleted");
    }

    @Operation(summary = "Resolve a short code or alias")
    @GetMapping("/{shortCodeOrAlias}")
    public ResponseEntity<ShortUrlResponse> resolve(@PathVariable String shortCodeOrAlias) {
        ShortCode mapping = shortenService.resolveShortCodeOrAlias(shortCodeOrAlias);
        return ResponseEntity.ok(ShortUrlResponse.builder()
                .shortUrl(mapping.getShortCode())
                .aliasUrl(mapping.getAlias())
                .passwordProtected(mapping.getIsProtected())
                .build());
    }
}
