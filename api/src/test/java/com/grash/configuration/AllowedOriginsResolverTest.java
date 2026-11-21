package com.grash.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AllowedOriginsResolverTest {

    private static final String RAILWAY_ORIGIN = "https://hospital-cmms-web-production.up.railway.app";

    private AllowedOriginsResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AllowedOriginsResolver();
    }

    @Test
    void resolve_mergesFrontendUrlsAndExtraOrigins() {
        ReflectionTestUtils.setField(resolver, "frontendUrl", " http://localhost:3000/ ");
        ReflectionTestUtils.setField(resolver, "frontendHomeUrl", "http://localhost:3001");
        ReflectionTestUtils.setField(resolver, "allowedOrigins", RAILWAY_ORIGIN);

        assertArrayEquals(
                new String[]{"http://localhost:3000", "http://localhost:3001", RAILWAY_ORIGIN},
                resolver.resolve());
    }

    @Test
    void resolve_ignoresWildcardsBlanksAndDuplicates() {
        ReflectionTestUtils.setField(resolver, "frontendUrl", RAILWAY_ORIGIN);
        ReflectionTestUtils.setField(resolver, "frontendHomeUrl", "");
        ReflectionTestUtils.setField(resolver, "allowedOrigins",
                RAILWAY_ORIGIN + "/, *, https://extra.example.com/ , ");

        String[] origins = resolver.resolve();

        assertArrayEquals(new String[]{RAILWAY_ORIGIN, "https://extra.example.com"}, origins);
        assertFalse(java.util.Arrays.asList(origins).contains("*"));
    }

    @Test
    void resolve_withoutConfiguredOrigins_returnsEmptyArray() {
        ReflectionTestUtils.setField(resolver, "frontendUrl", "");
        ReflectionTestUtils.setField(resolver, "frontendHomeUrl", "");
        ReflectionTestUtils.setField(resolver, "allowedOrigins", "");

        assertArrayEquals(new String[0], resolver.resolve());
    }
}
