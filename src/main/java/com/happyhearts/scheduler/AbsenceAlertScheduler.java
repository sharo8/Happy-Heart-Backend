package com.happyhearts.scheduler;

import com.happyhearts.service.NotificationService;
import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class AbsenceAlertScheduler {

    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 10 * * *", zone = "Africa/Kigali")
    public void alertAbsences() {
        LocalDate today = LocalDate.now(TimeUtils.kigali());
        notificationService.sendAbsenceAlerts(today);
    }
}
