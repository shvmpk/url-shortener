package com.shvmpk.shortener_service;

import com.shvmpk.shortener_service.config.AppProperties;
import com.shvmpk.shortener_service.util.UrlUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ShortenerServiceApplicationTests {

	@Autowired
	private AppProperties appProperties;

	@Autowired
	private UrlUtils urlUtils;

	@Test
	void contextLoads() {
	}

	@Test
	void baseUrlDefaultsToGateway() {
		assertThat(appProperties.baseUrl()).isEqualTo("http://localhost:8080");
	}

	@Test
	void isValidAcceptsWellFormedUrl() {
		assertThat(urlUtils.isValid("https://example.com")).isTrue();
	}

	@Test
	void isValidRejectsMalformedUrl() {
		assertThat(urlUtils.isValid("not a url")).isFalse();
	}

	@Test
	void normalizeDeduplicatesAndSorts() {
		String result = urlUtils.normalize(" HTTPS://Example.com/Path/?b=2&a=1 ");
		assertThat(result).isEqualTo("https://example.com/Path?a=1&b=2");
	}

	@Test
	void normalizeStripsTrailingSlash() {
		assertThat(urlUtils.normalize("http://example.com/foo/bar/"))
				.isEqualTo("http://example.com/foo/bar");
	}

	@Test
	void normalizeRemovesDefaultPort() {
		assertThat(urlUtils.normalize("http://example.com:80/page"))
				.isEqualTo("http://example.com/page");
	}

	@Test
	void normalizeKeepsNonDefaultPort() {
		assertThat(urlUtils.normalize("http://example.com:8080/page"))
				.isEqualTo("http://example.com:8080/page");
	}

	@Test
	void normalizeStripsWwwPrefix() {
		assertThat(urlUtils.normalize("http://www.example.com/path"))
				.isEqualTo("http://example.com/path");
	}

	@Test
	void normalizeThrowsOnInvalidUrl() {
		assertThrows(IllegalArgumentException.class, () ->
				urlUtils.normalize("not a url"));
	}

}
