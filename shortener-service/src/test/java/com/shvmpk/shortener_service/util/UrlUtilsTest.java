package com.shvmpk.shortener_service.util;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class UrlUtilsTest {

    private HttpServer server;
    private UrlUtils urlUtils;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

        server.createContext("/", exchange -> respond(exchange, 200));
        server.createContext("/path", exchange -> respond(exchange, 404));
        server.createContext("/forbidden", exchange -> respond(exchange, 403));
        server.createContext("/server-error", exchange -> respond(exchange, 500));
        server.createContext("/head-not-allowed", exchange -> {
            if ("HEAD".equals(exchange.getRequestMethod())) {
                respond(exchange, 405);
            } else {
                respond(exchange, 200);
            }
        });
        server.createContext("/requires-user-agent", exchange -> {
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            if (userAgent == null || userAgent.isBlank()) {
                respond(exchange, 403);
            } else {
                respond(exchange, 200);
            }
        });

        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        urlUtils = new UrlUtils();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        OutputStream os = exchange.getResponseBody();
        os.close();
    }

    @Test
    void rootUrl_returns200_isReachable() {
        assertThat(urlUtils.isReachable(baseUrl + "/")).isTrue();
    }

    @Test
    void pathUrl_returns404_isStillReachable() {
        assertThat(urlUtils.isReachable(baseUrl + "/path")).isTrue();
    }

    @Test
    void pathUrl_returns403_isStillReachable() {
        assertThat(urlUtils.isReachable(baseUrl + "/forbidden")).isTrue();
    }

    @Test
    void serverError_isNotReachable() {
        assertThat(urlUtils.isReachable(baseUrl + "/server-error")).isFalse();
    }

    @Test
    void headRejected_fallsBackToGet_isReachable() {
        assertThat(urlUtils.isReachable(baseUrl + "/head-not-allowed")).isTrue();
    }

    @Test
    void requestSendsUserAgentHeader() {
        assertThat(urlUtils.isReachable(baseUrl + "/requires-user-agent")).isTrue();
    }

    @Test
    void connectionFailure_isNotReachable() {
        assertThat(urlUtils.isReachable("http://localhost:1")).isFalse();
    }
}
