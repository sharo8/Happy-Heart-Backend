package com.happyhearts.config;

import com.happyhearts.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RfidApiKeyFilter extends OncePerRequestFilter {

    private final RfidProperties rfidProperties;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${rfid.api-key:}")
    private String legacyApiKey;

    private static final Set<String> PROTECTED_POST = Set.of(
            "/api/v1/attendance/scan",
            "/api/v1/attendance/sync-offline",
            "/api/v1/attendance/unknown-scan",
            "/api/attendance/scan",
            "/api/attendance/sync-offline",
            "/api/attendance/sync-offline-batch",
            "/api/attendance/heartbeat"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest req,
            @NonNull HttpServletResponse res,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {
        String path = req.getRequestURI();
        String method = req.getMethod();

        boolean protectedPut = "PUT".equals(method) && path.matches(".*/api/v1/devices/[^/]+/heartbeat");
        boolean protectedPost = "POST".equals(method) && PROTECTED_POST.stream().anyMatch(path::endsWith);

        if (!protectedPost && !protectedPut) {
            chain.doFilter(req, res);
            return;
        }

        if (authenticateWithBearerDeviceJwt(req)) {
            chain.doFilter(req, res);
            return;
        }

        String expected = resolveExpectedKey();
        if (expected != null && !expected.isBlank()) {
            String provided = req.getHeader("X-RFID-API-KEY");
            if (provided == null || !provided.equals(expected)) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                res.getWriter().write("{\"error\":\"Invalid or missing X-RFID-API-KEY header\"}");
                return;
            }
        }

        setRfidReaderAuthentication(req, "rfid-reader");
        chain.doFilter(req, res);
    }

    private String resolveExpectedKey() {
        if (StringUtils.hasText(legacyApiKey)) {
            return legacyApiKey;
        }
        return rfidProperties.getApiKey();
    }

    private boolean authenticateWithBearerDeviceJwt(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return false;
        }
        String token = header.substring(7);
        if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isDeviceAccessToken(token)) {
            return false;
        }
        String deviceId = jwtTokenProvider.getDeviceId(token);
        setRfidReaderAuthentication(request, deviceId != null ? deviceId : "rfid-device");
        return true;
    }

    private void setRfidReaderAuthentication(HttpServletRequest request, String principalName) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principalName,
                null,
                List.of(new SimpleGrantedAuthority("RFID_READER"))
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
