package com.grash.configuration;

import com.grash.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;


@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private static final long MAX_AGE_SECS = 3600;
    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");
    private static final List<String> ALLOWED_HEADERS =
            List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With");

    private final CurrentUserResolver currentUserResolver;
    private final AllowedOriginsResolver allowedOriginsResolver;
    @Value("${security.cors.enabled}")
    private boolean enableCors;

    /**
     * Spring Security picks this configuration up through HandlerMappingIntrospector and
     * installs its CorsFilter ahead of the authentication filters. Preflight requests are
     * answered there and never reach JWT authentication.
     */
    @Override
    public void addCorsMappings(@NotNull CorsRegistry registry) {
        if (!enableCors) {
            return;
        }

        registry.addMapping("/**")
                .allowedOrigins(allowedOriginsResolver.resolve())
                .allowedMethods(ALLOWED_METHODS.toArray(String[]::new))
                .allowedHeaders(ALLOWED_HEADERS.toArray(String[]::new))
                .allowCredentials(true)
                .maxAge(MAX_AGE_SECS);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(currentUserResolver);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/")
                .addResourceLocations("file:/app/static/images/")
                .addResourceLocations("file:/app/static/config/");
    }
}
