package com.happyhearts.scheduler;

import com.happyhearts.service.NotificationService;
import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DailyReportScheduler {

    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 18 * * *", zone = "Africa/Kigali")
    public void sendReports() {
        LocalDate today = LocalDate.now(TimeUtils.kigali());
        notificationService.sendDailyReportsForAllBranches(today);
    }
}
