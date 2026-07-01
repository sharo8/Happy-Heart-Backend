package com.happyhearts.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happyhearts.dto.ApiResponse;
import com.happyhearts.enums.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.happyhearts.service.GmPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Blocks mutating HTTP methods for {@link Role#GENERAL_MANAGER_PEDAGOGIQUE} except self-service profile,
 * auth refresh/logout, feedback creation, and internal messaging.
 */
@Component
@RequiredArgsConstructor
public class GmpWriteBlockingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final GmPermissionService gmPermissionService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!requiresBlock(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail("error.gmp.readonly"));
    }

    private boolean requiresBlock(HttpServletRequest request) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal up)) {
            return false;
        }
        if (up.getRole() != Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            return false;
        }
        String m = request.getMethod();
        if ("GET".equalsIgnoreCase(m) || "HEAD".equalsIgnoreCase(m) || "OPTIONS".equalsIgnoreCase(m)) {
            return false;
        }
        if (isAllowedMutation(request, up)) {
            return false;
        }
        return true;
    }

    private boolean isAllowedMutation(HttpServletRequest request, UserPrincipal up) {
        if (isWhitelistedMutation(request)) {
            return true;
        }
        return gmPermissionService.isHttpMutationAllowed(
                up.getId(),
                request.getRequestURI(),
                request.getMethod()
        );
    }

    private boolean isWhitelistedMutation(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method) && "/api/v1/ai/query".equals(uri)) {
            return true;
        }
        if (uri.equals("/api/v1/me") || uri.startsWith("/api/v1/me/")) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && ("/api/v1/auth/logout".equals(uri) || "/api/v1/auth/refresh".equals(uri))) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/v1/conversations".equals(uri)) {
            return true;
        }
        if (uri.startsWith("/api/v1/emails")) {
            return true;
        }
        return "POST".equalsIgnoreCase(method)
                && uri.startsWith("/api/v1/conversations/")
                && uri.endsWith("/messages");
    }
}
