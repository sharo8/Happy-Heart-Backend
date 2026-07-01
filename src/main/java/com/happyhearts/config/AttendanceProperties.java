package com.happyhearts.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.attendance")
public class AttendanceProperties {

    private LocalTime workStartTime = LocalTime.of(7, 30);
    private LocalTime workEndTime = LocalTime.of(17, 0);
    private int gracePeriodMinutes = 15;
    private int duplicateScanWindowMinutes = 5;
    /** Minimum minutes between check-in and check-out (both directions). */
    private int minimumMinutesBeforeCheckout = 5;
    /** Cooldown before the same unassigned card can be scanned again. */
    private int unknownCardCooldownMinutes = 2;
    private int halfDayMaxHours = 4;
    private String branchTimezone = "Africa/Kigali";
    /** Show "request explanation" when daily check-outs exceed this count. */
    private int maxCheckoutsPerDay = 2;
}
