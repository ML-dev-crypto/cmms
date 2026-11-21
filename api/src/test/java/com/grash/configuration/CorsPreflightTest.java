package com.grash.configuration;

import com.grash.security.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Exercises the exact CorsFilter Spring Security installs ahead of the authentication
 * filters, built from the real WebMvcConfig CORS mappings.
 */
class CorsPreflightTest {

    private static final String FRONTEND_ORIGIN = "https://hospital-cmms-web-production.up.railway.app";

    private static class CapturingCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }

    private CorsFilter corsFilter;

    @BeforeEach
    void setUp() {
        AllowedOriginsResolver allowedOriginsResolver = new AllowedOriginsResolver();
        ReflectionTestUtils.setField(allowedOriginsResolver, "frontendUrl", "https://legacy-frontend.example.com");
        ReflectionTestUtils.setField(allowedOriginsResolver, "frontendHomeUrl", "");
        ReflectionTestUtils.setField(allowedOriginsResolver, "allowedOrigins", FRONTEND_ORIGIN);
        WebMvcConfig config = new WebMvcConfig(mock(CurrentUserResolver.class), allowedOriginsResolver);
        ReflectionTestUtils.setField(config, "enableCors", true);

        CapturingCorsRegistry registry = new CapturingCorsRegistry();
        config.addCorsMappings(registry);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        for (Map.Entry<String, CorsConfiguration> entry : registry.configurations().entrySet()) {
            source.registerCorsConfiguration(entry.getKey(), entry.getValue());
        }
        corsFilter = new CorsFilter(source);
    }

    @Test
    void preflightToLicenseState_returnsExactAllowedOrigin() throws Exception {
        MockHttpServletResponse response = preflight("/license/state", "GET", null);

        assertEquals(200, response.getStatus());
        assertEquals(FRONTEND_ORIGIN, response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertNotEquals("*", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertTrue(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS).contains("GET"));
        assertEquals("true", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void preflightToSignin_allowsAuthorizationAndContentType() throws Exception {
        MockHttpServletResponse response = preflight("/auth/signin", "POST", "authorization,content-type");

        assertEquals(200, response.getStatus());
        assertEquals(FRONTEND_ORIGIN, response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        String allowedHeaders = response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        assertNotNull(allowedHeaders);
        assertTrue(allowedHeaders.toLowerCase().contains("authorization"));
        assertTrue(allowedHeaders.toLowerCase().contains("content-type"));
        assertTrue(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS).contains("POST"));
    }

    @Test
    void preflightDoesNotReachDownstreamFilters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/auth/signin");
        request.addHeader(HttpHeaders.ORIGIN, FRONTEND_ORIGIN);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        corsFilter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNull(chain.getRequest(), "Preflight must be answered before the JWT filters run");
    }

    @Test
    void preflightFromUnknownOrigin_isRejectedWithoutCorsHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/license/state");
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.example.com");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        MockHttpServletResponse response = new MockHttpServletResponse();

        corsFilter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertNull(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void actualRequest_fromAllowedOrigin_hasExactAllowedOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/license/state");
        request.addHeader(HttpHeaders.ORIGIN, FRONTEND_ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        corsFilter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest(), "Non-preflight requests continue down the security chain");
        assertEquals(FRONTEND_ORIGIN, response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private MockHttpServletResponse preflight(String path, String method, String requestedHeaders) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader(HttpHeaders.ORIGIN, FRONTEND_ORIGIN);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
        if (requestedHeaders != null) {
            request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, requestedHeaders);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        corsFilter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
