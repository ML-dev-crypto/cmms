package com.grash.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Single source of truth for the browser origins accepted by the API. The REST CORS
 * mapping and the SockJS/STOMP endpoint both resolve their allowlist here so they
 * cannot drift apart (a mismatch rejects the SockJS info request with 403 before the
 * STOMP handshake is ever attempted).
 */
@Component
@Slf4j
public class AllowedOriginsResolver {

    @Value("${frontend.url}")
    private String frontendUrl;
    @Value("${frontend.home-url:}")
    private String frontendHomeUrl;
    @Value("${frontend.allowed-origins:}")
    private String allowedOrigins;

    public String[] resolve() {
        return Stream.of(frontendUrl, frontendHomeUrl, allowedOrigins)
                .filter(value -> value != null)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .map(AllowedOriginsResolver::stripTrailingSlash)
                .filter(origin -> !origin.isEmpty())
                .filter(origin -> {
                    if ("*".equals(origin)) {
                        log.warn("Ignoring wildcard '*' origin; wildcards are not allowed with credentials");
                        return false;
                    }
                    return true;
                })
                .distinct()
                .toArray(String[]::new);
    }

    private static String stripTrailingSlash(String origin) {
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }
}
