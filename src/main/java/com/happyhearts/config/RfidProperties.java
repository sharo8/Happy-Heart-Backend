package com.happyhearts.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.rfid")
public class RfidProperties {

    private String apiKey = "";
    private int scanRatePerSecond = 10;
}
