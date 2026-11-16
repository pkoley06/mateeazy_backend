package com.matcher.matchmate_backend.config;

import com.matcher.matchmate_backend.service.AdminBrandingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rejects every community endpoint (feed, follows, statuses) while the admin has
 * the community feature switched off, so a stale or modified client cannot reach it.
 */
@Component
public class CommunityFeatureInterceptor implements HandlerInterceptor {

    private final AdminBrandingService brandingService;

    public CommunityFeatureInterceptor(AdminBrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if (!Boolean.FALSE.equals(brandingService.getEntity().getCommunityEnabled())) return true;
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Community is currently unavailable\"}");
        return false;
    }

    @Configuration
    static class Registration implements WebMvcConfigurer {
        private final CommunityFeatureInterceptor interceptor;

        Registration(CommunityFeatureInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(interceptor).addPathPatterns(
                    "/api/v1/community/**",
                    "/api/v1/statuses/**",
                    "/api/v1/users/*/follow",
                    "/api/v1/users/*/unfollow",
                    "/api/v1/users/*/followers",
                    "/api/v1/users/*/following",
                    "/api/v1/users/*/follow-status",
                    "/api/v1/users/*/follow-counts",
                    "/api/v1/users/follows/**",
                    "/api/v1/users/privacy/direct-follow");
        }
    }
}
