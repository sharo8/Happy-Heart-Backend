package com.happyhearts.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.sendgrid")
public class SendGridProperties {

    private String apiKey = "";
    private String fromEmail = "";
    private String fromName = "";
}
