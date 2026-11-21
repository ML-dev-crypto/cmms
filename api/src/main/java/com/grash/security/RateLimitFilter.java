package com.grash.security;

import com.grash.exception.CustomException;
import com.grash.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that applies rate limiting to authenticated users based on their user ID.
 * This filter should be added after authentication filters (JwtTokenFilter, ApiKeyAuthFilter)
 * to ensure the user is already authenticated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {
        // Skip rate limiting if disabled
        if (!rateLimiterService.isRateLimitEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get the current authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof CustomUserDetail userDetail) {
            Long userId = userDetail.getUser().getId();
            String userIdKey = "user:" + userId;

            // Check rate limit
            if (!rateLimiterService.resolveAuthenticatedUserBucket(userIdKey).tryConsume(1)) {
                log.warn("Rate limit exceeded for user ID: {}", userId);
                setResponseTooManyRequests(response);
                return;
            }
        } else {
            String clientIp = clientIpResolver.resolve(request);
            if (!rateLimiterService.resolveUnAuthenticatedUserBucket(clientIp).tryConsume(1)) {
                setResponseTooManyRequests(response);
                return;
            }
        }

        // Continue the filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Hosting platforms poll the health endpoint every few seconds. Without an
     * exemption the unauthenticated long-term bucket would answer 429 and the
     * platform would keep restarting the instance.
     */
    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return "/health-check".equals(path);
    }

    private static void setResponseTooManyRequests(@NotNull HttpServletResponse response) throws IOException {
        response.setStatus(429); // HTTP 429 Too Many Requests
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests. " +
                "Please try again later.\"}");
    }
}
