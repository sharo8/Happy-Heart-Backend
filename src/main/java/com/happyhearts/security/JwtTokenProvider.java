package com.happyhearts.security;

import com.happyhearts.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_MUST_CHANGE_PASSWORD = "mch";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String TYPE_DEVICE = "device";
    private static final String CLAIM_DEVICE_ID = "deviceId";

    private final JwtProperties jwtProperties;

    public String createAccessToken(UserPrincipal principal) {
        return createAccessToken(principal, principal.isPasswordChangeRequired());
    }

    public String createAccessToken(UserPrincipal principal, boolean mustChangePassword) {
        return buildAccessToken(principal.getUsername(), mustChangePassword, jwtProperties.getExpirationMs());
    }

    public String createRefreshToken(UserPrincipal principal) {
        return buildToken(principal.getUsername(), TYPE_REFRESH, jwtProperties.getRefreshExpirationMs());
    }

    public String createDeviceAccessToken(String deviceId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + jwtProperties.getDeviceExpirationMs());
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TYPE_DEVICE);
        claims.put(CLAIM_DEVICE_ID, deviceId);
        return Jwts.builder()
                .subject("rfid-device:" + deviceId)
                .claims(claims)
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey())
                .compact();
    }

    public Instant deviceTokenExpiresAt() {
        return Instant.now().plusMillis(jwtProperties.getDeviceExpirationMs());
    }

    public boolean isDeviceAccessToken(String token) {
        return TYPE_DEVICE.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
    }

    public String getDeviceId(String token) {
        return parseClaims(token).get(CLAIM_DEVICE_ID, String.class);
    }

    private String buildAccessToken(String subject, boolean mustChangePassword, long ttlMs) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttlMs);
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TYPE_ACCESS);
        if (mustChangePassword) {
            claims.put(CLAIM_MUST_CHANGE_PASSWORD, true);
        }
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey())
                .compact();
    }

    private String buildToken(String subject, String type, long ttlMs) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttlMs);
        return Jwts.builder()
                .subject(subject)
                .claims(Map.of(CLAIM_TYPE, type))
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey())
                .compact();
    }

    public String getSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
