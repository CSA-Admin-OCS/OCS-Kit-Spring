package com.open.spring.system.kit;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Returns 404 for requests under a disabled feature's path prefix (kits/catalog.json spring.paths).
 * Registered ahead of Spring Security (see {@link KitConfig}), so a disabled endpoint looks the
 * same to everyone instead of answering 401/403.
 */
public class FeatureGateFilter extends OncePerRequestFilter {

    private final KitFeatures features;
    private final UrlPathHelper pathHelper = new UrlPathHelper();

    public FeatureGateFilter(KitFeatures features) {
        this.features = features;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Decoded, without context path or ;jsessionid, matching what Spring MVC routes on.
        String path = pathHelper.getPathWithinApplication(request);
        String disabled = features.disabledFeatureForPath(path);
        if (disabled == null) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"Not Found\",\"feature\":\"" + disabled
                + "\",\"message\":\"This feature is not enabled in this kit.\"}");
    }
}
