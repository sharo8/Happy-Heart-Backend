package com.happyhearts.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret = "";
    private long expirationMs = 86400000L;
    private long refreshExpirationMs = 604800000L;
    /** Long-lived JWT for ESP32 / RFID reader devices (default ~1 year). */
    private long deviceExpirationMs = 31_536_000_000L;
}
