package com.shvmpk.shortener_service.util;

import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

@Component
public class UrlUtils implements UrlValidator {

    private static final Logger log = LoggerFactory.getLogger(UrlUtils.class);

    @Override
    public boolean isValid(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; URLShortenerBot/1.0)";

    @Override
    @Retry(name = "url-verify", fallbackMethod = "isReachableFallback")
    public boolean isReachable(String urlStr) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlStr).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_BAD_METHOD
                    || responseCode == HttpURLConnection.HTTP_NOT_IMPLEMENTED) {
                connection.disconnect();
                connection = (HttpURLConnection) new URL(urlStr).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                responseCode = connection.getResponseCode();
            }

            return responseCode > 0 && responseCode < 500;
        } catch (IOException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isReachableFallback(String urlStr, Throwable t) {
        log.warn("URL reachability check failed after retries: {}", t.getMessage());
        return false;
    }

    @Override
    public String normalize(String inputUrl) {
        try {
            URL url = new URL(inputUrl.trim());

            String protocol = url.getProtocol().toLowerCase();
            String host = url.getHost().toLowerCase();
            if (host.startsWith("www.")) host = host.substring(4);

            int port = url.getPort();
            String portPart = (port == -1 || port == url.getDefaultPort()) ? "" : ":" + port;

            String path = url.getPath().replaceAll("/+$", "");

            String query = url.getQuery();
            String sortedQuery = "";
            if (query != null) {
                List<String> params = Arrays.asList(query.split("&"));
                params.sort(String::compareTo);
                sortedQuery = String.join("&", params);
            }

            return protocol + "://" + host + portPart + path +
                    (sortedQuery.isEmpty() ? "" : "?" + sortedQuery);

        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL format for normalization");
        }
    }
}
