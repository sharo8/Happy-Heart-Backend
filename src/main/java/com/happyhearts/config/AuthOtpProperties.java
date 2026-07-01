package com.happyhearts.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthOtpProperties {

    private int otpExpirationMinutes = 10;
    private int otpLength = 6;
    private int otpMaxAttempts = 5;
    private int setupTokenExpirationHours = 72;
    private int passwordResetTokenExpirationHours = 24;
    private String frontendBaseUrl = "http://localhost:3000";
}
