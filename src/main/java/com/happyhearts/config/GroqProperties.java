package com.happyhearts.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "groq.api")
public class GroqProperties {

    private String key = "";
    private String model = "llama-3.3-70b-versatile";
    private String baseUrl = "https://api.groq.com/openai/v1";
}
