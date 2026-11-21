package com.grash.configuration;

import com.grash.security.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class WebMvcConfigTest {

    private static final String RAILWAY_ORIGIN = "https://hospital-cmms-web-production.up.railway.app";

    private static class ExposedCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> getConfigurations() {
            return getCorsConfigurations();
        }
    }

    @Mock
    private CurrentUserResolver currentUserResolver;

    private AllowedOriginsResolver allowedOriginsResolver;
    private WebMvcConfig webMvcConfig;

    @BeforeEach
    void setUp() {
        allowedOriginsResolver = new AllowedOriginsResolver();
        ReflectionTestUtils.setField(allowedOriginsResolver, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(allowedOriginsResolver, "frontendHomeUrl", "http://localhost:3001");
        ReflectionTestUtils.setField(allowedOriginsResolver, "allowedOrigins", RAILWAY_ORIGIN);
        webMvcConfig = new WebMvcConfig(currentUserResolver, allowedOriginsResolver);
        ReflectionTestUtils.setField(webMvcConfig, "enableCors", true);
    }

    @Test
    void addCorsMappings_whenEnabled_configuresAllowedOrigins() {
        ExposedCorsRegistry registry = new ExposedCorsRegistry();

        webMvcConfig.addCorsMappings(registry);

        CorsConfiguration configuration = registry.getConfigurations().get("/**");
        assertEquals(
                List.of("http://localhost:3000", "http://localhost:3001", RAILWAY_ORIGIN),
                configuration.getAllowedOrigins());
        assertTrue(configuration.getAllowedMethods().containsAll(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")));
        assertTrue(configuration.getAllowedHeaders().containsAll(
                List.of("Authorization", "Content-Type")));
        assertTrue(configuration.getAllowCredentials());
        assertEquals(3600, configuration.getMaxAge());
    }

    @Test
    void addCorsMappings_whenDisabled_registersNoCorsMapping() {
        ReflectionTestUtils.setField(webMvcConfig, "enableCors", false);
        ExposedCorsRegistry registry = new ExposedCorsRegistry();

        webMvcConfig.addCorsMappings(registry);

        assertTrue(registry.getConfigurations().isEmpty());
    }

    @Test
    void addCorsMappings_ignoresWildcardOrigin() {
        ReflectionTestUtils.setField(allowedOriginsResolver, "allowedOrigins", "*, https://extra.example.com");

        ExposedCorsRegistry registry = new ExposedCorsRegistry();
        webMvcConfig.addCorsMappings(registry);

        List<String> origins = registry.getConfigurations().get("/**").getAllowedOrigins();
        assertFalse(origins.contains("*"));
        assertTrue(origins.contains("https://extra.example.com"));
    }

    @Test
    void addCorsMappings_normalizesTrailingSlashAndDuplicates() {
        ReflectionTestUtils.setField(allowedOriginsResolver, "frontendUrl", "https://app.example.com/");
        ReflectionTestUtils.setField(allowedOriginsResolver, "allowedOrigins", " https://app.example.com , ");

        ExposedCorsRegistry registry = new ExposedCorsRegistry();
        webMvcConfig.addCorsMappings(registry);

        List<String> origins = registry.getConfigurations().get("/**").getAllowedOrigins();
        assertEquals(List.of("https://app.example.com", "http://localhost:3001"), origins);
    }

    @Test
    void addArgumentResolvers_addsCurrentUserResolver() {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

        webMvcConfig.addArgumentResolvers(resolvers);

        assertEquals(1, resolvers.size());
        assertSame(currentUserResolver, resolvers.get(0));
    }

    @Test
    void addResourceHandlers_mapsImagesPattern() {
        ResourceHandlerRegistry registry =
                new ResourceHandlerRegistry(mock(ApplicationContext.class), null);

        webMvcConfig.addResourceHandlers(registry);

        assertTrue(registry.hasMappingForPattern("/images/**"));
    }
}
