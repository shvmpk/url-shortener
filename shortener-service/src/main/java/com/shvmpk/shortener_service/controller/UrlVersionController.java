package com.shvmpk.shortener_service.controller;

import com.shvmpk.shortener_service.dto.UrlVersionResponse;
import com.shvmpk.shortener_service.model.ShortCode;
import com.shvmpk.shortener_service.service.ShortenService;
import com.shvmpk.shortener_service.service.UrlVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/urls/{shortCodeOrAlias}/versions")
@Tag(name = "URL Versions", description = "Operations with URL versions")
public class UrlVersionController {
    private final ShortenService shortenService;
    private final UrlVersionService urlVersionService;

    @Operation(summary = "Fetch all versions")
    @GetMapping
    public ResponseEntity<List<UrlVersionResponse>> getAllVersions(@PathVariable String shortCodeOrAlias) {
        ShortCode mapping = shortenService.resolveShortCodeOrAlias(shortCodeOrAlias);
        return ResponseEntity.ok(urlVersionService.getVersionsByShortCodeOrAlias(mapping));
    }

    @Operation(summary = "Get current version")
    @GetMapping("/current")
    public ResponseEntity<UrlVersionResponse> getCurrentVersion(@PathVariable String shortCodeOrAlias) {
        ShortCode mapping = shortenService.resolveShortCodeOrAlias(shortCodeOrAlias);
        return ResponseEntity.ok(urlVersionService.getCurrentVersion(mapping));
    }

    @Operation(summary = "Compare two versions")
    @GetMapping("/compare")
    public ResponseEntity<Map<String, Map<String, Object>>> compareVersions(
            @PathVariable String shortCodeOrAlias,
            @RequestParam Integer from,
            @RequestParam Integer to
    ) {
        ShortCode mapping = shortenService.resolveShortCodeOrAlias(shortCodeOrAlias);
        return ResponseEntity.ok(urlVersionService.compareVersions(mapping, from, to));
    }

    @Operation(summary = "Rollback to a specific version")
    @PostMapping("/rollback-to/{versionNumber}")
    public ResponseEntity<UrlVersionResponse> rollbackToVersion(
            @PathVariable String shortCodeOrAlias,
            @PathVariable Integer versionNumber
    ) {
        ShortCode mapping = shortenService.resolveShortCodeOrAlias(shortCodeOrAlias);
        return ResponseEntity.ok(urlVersionService.rollbackToVersion(mapping, versionNumber));
    }

    @Operation(summary = "Delete a specific version")
    @DeleteMapping("/{versionNumber}")
    public ResponseEntity<String> deleteSpecificVersion(
            @PathVariable String shortCodeOrAlias,
            @PathVariable Integer versionNumber
    ) {
        ShortCode mapping = shortenService.resolveShortCodeOrAlias(shortCodeOrAlias);
        return ResponseEntity.ok(urlVersionService.deleteSpecificVersion(mapping, versionNumber));
    }
}
