package com.happyhearts.scheduler;

import com.happyhearts.service.AttendanceSummaryService;
import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class AttendanceSummaryScheduler {

    private final AttendanceSummaryService attendanceSummaryService;

    @Scheduled(cron = "0 59 23 * * *", zone = "Africa/Kigali")
    public void closeDay() {
        LocalDate today = LocalDate.now(TimeUtils.kigali());
        attendanceSummaryService.summarizeDayForAllBranches(today);
    }
}
