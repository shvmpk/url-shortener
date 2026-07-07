package com.shvmpk.shortener_service.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrRequest {
    @Builder.Default
    private int size = 250;

    private String color;

    private String overlayText;

    private String logoUrl;

    @Pattern(regexp = "^(png|jpg|jpeg|svg|base64)$", flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Only PNG, JPG, JPEG, SVG, or BASE64 formats are allowed.")
    @Builder.Default
    private String format = "png";
}
