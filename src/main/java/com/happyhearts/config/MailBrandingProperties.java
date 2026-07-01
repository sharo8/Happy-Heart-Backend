package com.happyhearts.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Absolute URL to the Happy Hearts logo for HTML emails. When empty, templates fall back to styled text branding.
 * Default matches the public logo asset in {@code https://github.com/sharo8/Happy-Heart-Logo}.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.mail")
public class MailBrandingProperties {

    private String brandLogoUrl = "https://raw.githubusercontent.com/sharo8/Happy-Heart-Logo/main/LogoHappyHeart.jpeg";
}
