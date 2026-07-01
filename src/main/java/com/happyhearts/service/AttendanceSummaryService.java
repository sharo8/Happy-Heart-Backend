package com.happyhearts.service;

import com.happyhearts.config.AttendanceProperties;
import com.happyhearts.enums.AttendanceStatus;
import com.happyhearts.enums.ScanType;
import com.happyhearts.model.AttendanceRecord;
import com.happyhearts.model.Branch;
import com.happyhearts.model.DailyAttendanceSummary;
import com.happyhearts.model.Employee;
import com.happyhearts.repository.AttendanceRecordRepository;
import com.happyhearts.repository.BranchRepository;
import com.happyhearts.repository.DailyAttendanceSummaryRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceSummaryService {

    private final BranchRepository branchRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final DailyAttendanceSummaryRepository dailyAttendanceSummaryRepository;
    private final AttendanceProperties attendanceProperties;

    @Transactional
    public void summarizeDayForAllBranches(LocalDate day) {
        branchRepository.findAll().forEach(b -> summarizeBranchDay(b.getId(), day));
    }

    @Transactional
    public void summarizeBranchDay(UUID branchId, LocalDate day) {
        Branch branch = branchRepository.findById(branchId).orElseThrow();
        Instant start = TimeUtils.startOfDayKigali(day);
        Instant end = TimeUtils.endOfDayKigali(day);
        List<Employee> employees = employeeRepository.findByBranch(branch);
        for (Employee employee : employees) {
            List<AttendanceRecord> records = attendanceRecordRepository
                    .findByEmployee_IdAndScannedAtBetweenOrderByScannedAtAsc(employee.getId(), start, end);
            DailyAttendanceSummary summary = buildSummary(employee, branch, day, records);
            dailyAttendanceSummaryRepository.findByEmployee_IdAndSummaryDate(employee.getId(), day)
                    .ifPresentOrElse(
                            existing -> updateExisting(existing, summary),
                            () -> dailyAttendanceSummaryRepository.save(summary)
                    );
        }
    }

    private void updateExisting(DailyAttendanceSummary existing, DailyAttendanceSummary computed) {
        existing.setEntryTime(computed.getEntryTime());
        existing.setExitTime(computed.getExitTime());
        existing.setTotalHours(computed.getTotalHours());
        existing.setStatus(computed.getStatus());
        existing.setLate(computed.isLate());
        existing.setLateMinutes(computed.getLateMinutes());
        dailyAttendanceSummaryRepository.save(existing);
    }

    private DailyAttendanceSummary buildSummary(
            Employee employee,
            Branch branch,
            LocalDate day,
            List<AttendanceRecord> records
    ) {
        List<AttendanceRecord> entries = records.stream()
                .filter(r -> r.getScanType() == ScanType.ENTRY)
                .sorted(Comparator.comparing(AttendanceRecord::getScannedAt))
                .toList();
        List<AttendanceRecord> exits = records.stream()
                .filter(r -> r.getScanType() == ScanType.EXIT)
                .sorted(Comparator.comparing(AttendanceRecord::getScannedAt))
                .toList();

        if (entries.isEmpty()) {
            return DailyAttendanceSummary.builder()
                    .employee(employee)
                    .branch(branch)
                    .summaryDate(day)
                    .status(AttendanceStatus.ABSENT)
                    .late(false)
                    .lateMinutes(0)
                    .build();
        }

        AttendanceRecord firstEntry = entries.get(0);
        Instant entryInstant = firstEntry.getScannedAt();
        LocalTime entryLocal = TimeUtils.toKigaliTime(entryInstant);

        LocalTime workStart = branch.getWorkStartTime() != null
                ? branch.getWorkStartTime()
                : attendanceProperties.getWorkStartTime();
        int grace = branch.getGracePeriodMinutes() != null
                ? branch.getGracePeriodMinutes()
                : attendanceProperties.getGracePeriodMinutes();
        LocalTime threshold = workStart.plusMinutes(grace);
        boolean late = entryLocal.isAfter(threshold);
        int lateMinutes = 0;
        if (late) {
            lateMinutes = (int) Duration.between(
                    day.atTime(threshold).atZone(TimeUtils.kigali()).toInstant(),
                    entryInstant
            ).toMinutes();
            if (lateMinutes < 0) {
                lateMinutes = 0;
            }
        }

        LocalTime exitLocal = null;
        BigDecimal totalHours = null;
        AttendanceStatus status;

        if (exits.isEmpty()) {
            status = late ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
            return DailyAttendanceSummary.builder()
                    .employee(employee)
                    .branch(branch)
                    .summaryDate(day)
                    .entryTime(entryLocal)
                    .exitTime(null)
                    .totalHours(null)
                    .status(status)
                    .late(late)
                    .lateMinutes(lateMinutes)
                    .build();
        }

        AttendanceRecord lastExit = exits.get(exits.size() - 1);
        Instant exitInstant = lastExit.getScannedAt();
        exitLocal = TimeUtils.toKigaliTime(exitInstant);

        long seconds = Duration.between(entryInstant, exitInstant).getSeconds();
        if (seconds < 0) {
            seconds = 0;
        }
        totalHours = BigDecimal.valueOf(seconds / 3600.0).setScale(2, RoundingMode.HALF_UP);

        if (totalHours.compareTo(BigDecimal.valueOf(attendanceProperties.getHalfDayMaxHours())) < 0) {
            status = AttendanceStatus.HALF_DAY;
        } else if (late) {
            status = AttendanceStatus.LATE;
        } else {
            status = AttendanceStatus.PRESENT;
        }

        return DailyAttendanceSummary.builder()
                .employee(employee)
                .branch(branch)
                .summaryDate(day)
                .entryTime(entryLocal)
                .exitTime(exitLocal)
                .totalHours(totalHours)
                .status(status)
                .late(late)
                .lateMinutes(lateMinutes)
                .build();
    }
}
