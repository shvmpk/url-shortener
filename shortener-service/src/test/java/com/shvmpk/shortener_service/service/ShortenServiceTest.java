package com.shvmpk.shortener_service.service;

import com.shvmpk.shortener_service.config.AppProperties;
import com.shvmpk.shortener_service.dto.ShortUrlRequest;
import com.shvmpk.shortener_service.dto.ShortUrlResponse;
import com.shvmpk.shortener_service.exception.UrlConflictException;
import com.shvmpk.shortener_service.model.ShortCode;
import com.shvmpk.shortener_service.repository.UrlRepository;
import com.shvmpk.shortener_service.repository.UrlVersionRepository;
import com.shvmpk.shortener_service.util.PasswordService;
import com.shvmpk.shortener_service.util.ShortUrlGenerator;
import com.shvmpk.shortener_service.util.UrlValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortenServiceTest {

    @Mock
    private AppProperties appProperties;
    @Mock
    private UrlRepository urlRepository;
    @Mock
    private UrlVersionRepository urlVersionRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ShortUrlGenerator shortUrlGenerator;
    @Mock
    private PasswordService passwordService;
    @Mock
    private UrlValidator urlValidator;

    private ShortenService shortenService;

    private static final String LONG_URL = "https://google.com";
    private static final String NORMALIZED_URL = "https://google.com";

    @BeforeEach
    void setUp() {
        shortenService = new ShortenService(
                appProperties,
                urlRepository,
                urlVersionRepository,
                objectMapper,
                redisTemplate,
                shortUrlGenerator,
                passwordService,
                urlValidator,
                new SimpleMeterRegistry()
        );

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(urlValidator.isValid(anyString())).thenReturn(true);
        lenient().when(urlValidator.isReachable(anyString())).thenReturn(true);
        lenient().when(urlValidator.normalize(anyString())).thenReturn(NORMALIZED_URL);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
    }

    @Test
    void reShorteningExistingUnprotectedUrlAsProtected_throwsConflict() {
        ShortCode existingUnprotected = ShortCode.builder()
                .originalUrl(NORMALIZED_URL)
                .shortCode("abc123")
                .isProtected(false)
                .build();

        when(urlRepository.findFirstByOriginalUrlIgnoreCase(NORMALIZED_URL))
                .thenReturn(Optional.of(existingUnprotected));

        ShortUrlRequest request = ShortUrlRequest.builder()
                .longUrl(LONG_URL)
                .isProtected(true)
                .password("Str0ng!Pass")
                .build();

        assertThatThrownBy(() -> shortenService.shortenUrl(request))
                .isInstanceOf(UrlConflictException.class)
                .hasMessageContaining("different protection settings");

        verify(urlRepository, never()).save(any());
    }

    @Test
    void reShorteningExistingProtectedUrlAsUnprotected_throwsConflict() {
        ShortCode existingProtected = ShortCode.builder()
                .originalUrl(NORMALIZED_URL)
                .shortCode("abc123")
                .isProtected(true)
                .password("hashed-pw")
                .build();

        when(urlRepository.findFirstByOriginalUrlIgnoreCase(NORMALIZED_URL))
                .thenReturn(Optional.of(existingProtected));

        ShortUrlRequest request = ShortUrlRequest.builder()
                .longUrl(LONG_URL)
                .isProtected(false)
                .build();

        assertThatThrownBy(() -> shortenService.shortenUrl(request))
                .isInstanceOf(UrlConflictException.class)
                .hasMessageContaining("different protection settings");
    }

    @Test
    void reShorteningExistingUrlWithMatchingProtection_returnsExistingRecord() {
        ShortCode existingProtected = ShortCode.builder()
                .originalUrl(NORMALIZED_URL)
                .shortCode("abc123")
                .isProtected(true)
                .password("hashed-pw")
                .build();

        when(urlRepository.findFirstByOriginalUrlIgnoreCase(NORMALIZED_URL))
                .thenReturn(Optional.of(existingProtected));
        when(appProperties.baseUrl()).thenReturn("http://short.ly");

        ShortUrlRequest request = ShortUrlRequest.builder()
                .longUrl(LONG_URL)
                .isProtected(true)
                .password("Str0ng!Pass")
                .build();

        ShortUrlResponse response = shortenService.shortenUrl(request);

        assertThat(response.getShortUrl()).isEqualTo("http://short.ly/abc123");
        assertThat(response.getPasswordProtected()).isTrue();
        verify(valueOperations).set(eq("longUrl:" + DigestUtils
                .md5DigestAsHex(NORMALIZED_URL.getBytes())), eq("abc123"), any(java.time.Duration.class));
    }

    @Test
    void reShorteningExistingUnprotectedUrlAsUnprotected_returnsExistingRecordWithoutError() {
        ShortCode existingUnprotected = ShortCode.builder()
                .originalUrl(NORMALIZED_URL)
                .shortCode("abc123")
                .isProtected(false)
                .build();

        when(urlRepository.findFirstByOriginalUrlIgnoreCase(NORMALIZED_URL))
                .thenReturn(Optional.of(existingUnprotected));
        when(appProperties.baseUrl()).thenReturn("http://short.ly");

        ShortUrlRequest request = ShortUrlRequest.builder()
                .longUrl(LONG_URL)
                .isProtected(false)
                .build();

        ShortUrlResponse response = shortenService.shortenUrl(request);

        assertThat(response.getShortUrl()).isEqualTo("http://short.ly/abc123");
        assertThat(response.getPasswordProtected()).isFalse();
    }
}
