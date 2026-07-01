package com.happyhearts.service;

import com.happyhearts.enums.ScanType;
import com.happyhearts.model.AttendanceRecord;
import com.happyhearts.model.Employee;
import com.happyhearts.repository.AttendanceExcuseRepository;
import com.happyhearts.repository.GracePeriodRequestRepository;
import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceEvaluationService {

    private final WorkScheduleResolver workScheduleResolver;
    private final GracePeriodRequestRepository gracePeriodRequestRepository;
    private final AttendanceExcuseRepository attendanceExcuseRepository;
    private final ExpectedWorkDayService expectedWorkDayService;

    public record DayEvaluation(
            String status,
            int minutesLate,
            int earlyDepartureMinutes,
            int lostMinutes,
            double hoursWorked,
            boolean dayComplete,
            boolean excused,
            boolean graceApplied
    ) {}

    @Transactional(readOnly = true)
    public boolean isExcused(Employee employee, LocalDate day) {
        return attendanceExcuseRepository.hasExcuse(employee.getId(), day);
    }

    @Transactional(readOnly = true)
    public boolean hasApprovedGrace(Employee employee, LocalDate day) {
        return gracePeriodRequestRepository.hasApprovedGrace(employee.getId(), day);
    }

    public int minutesLate(Employee employee, Instant firstIn) {
        if (firstIn == null) {
            return 0;
        }
        return minutesLate(employee, firstIn, TimeUtils.toKigaliDate(firstIn));
    }

    private int minutesLate(Employee employee, Instant firstIn, LocalDate day) {
        if (firstIn == null) {
            return 0;
        }
        WorkScheduleResolver.EffectiveSchedule schedule = workScheduleResolver.resolve(employee, day);
        LocalTime arrival = TimeUtils.toKigaliTime(firstIn);
        LocalTime expected = schedule.workStart();
        if (!arrival.isAfter(expected)) {
            return 0;
        }
        return (int) ChronoUnit.MINUTES.between(expected, arrival);
    }

    public boolean isLateArrival(Employee employee, Instant firstIn) {
        return minutesLate(employee, firstIn) > 0;
    }

    @Transactional(readOnly = true)
    public DayEvaluation evaluateDay(Employee employee, LocalDate day, List<AttendanceRecord> logs) {
        boolean excused = attendanceExcuseRepository.hasExcuse(employee.getId(), day);
        if (excused) {
            return new DayEvaluation("excused", 0, 0, 0, 0, false, true, false);
        }

        List<AttendanceRecord> sorted = new ArrayList<>(logs == null ? List.of() : logs);
        sorted.sort(Comparator.comparing(AttendanceRecord::getScannedAt));

        Instant firstIn = null;
        Instant lastOut = null;
        ScanType lastType = null;
        for (AttendanceRecord log : sorted) {
            if (log.getScanType() == ScanType.ENTRY) {
                if (firstIn == null || log.getScannedAt().isBefore(firstIn)) {
                    firstIn = log.getScannedAt();
                }
            } else if (log.getScanType() == ScanType.EXIT) {
                if (lastOut == null || log.getScannedAt().isAfter(lastOut)) {
                    lastOut = log.getScannedAt();
                }
            }
            lastType = log.getScanType();
        }

        if (firstIn == null) {
            if (!expectedWorkDayService.isExpectedWorkDay(employee, day)) {
                return new DayEvaluation("off_day", 0, 0, 0, 0, false, false, false);
            }
            return new DayEvaluation("absent", 0, 0, 0, 0, false, false, false);
        }

        int minutesLate = minutesLate(employee, firstIn, day);
        boolean graceApplied = false;
        boolean late = minutesLate > 0;
        if (late && gracePeriodRequestRepository.hasApprovedGrace(employee.getId(), day)) {
            graceApplied = true;
            late = false;
        }

        int earlyDepartureMinutes = 0;
        boolean earlyDeparture = false;
        if (lastOut != null && lastType == ScanType.EXIT) {
            earlyDeparture = workScheduleResolver.isEarlyDeparture(employee, lastOut);
            if (earlyDeparture) {
                earlyDepartureMinutes = workScheduleResolver.earlyDepartureMinutes(employee, lastOut);
            }
        }

        double hoursWorked = 0;
        boolean dayComplete = firstIn != null && lastOut != null && lastType == ScanType.EXIT;
        if (firstIn != null && lastOut != null && lastOut.isAfter(firstIn)) {
            hoursWorked = Math.round(Duration.between(firstIn, lastOut).toMinutes() / 6.0) / 10.0;
        }

        int lostMinutes = (graceApplied ? 0 : minutesLate) + earlyDepartureMinutes;

        String status;
        if (graceApplied) {
            status = "grace";
        } else if (late) {
            status = "late";
        } else if (dayComplete && earlyDeparture) {
            status = "early_departure";
        } else if (dayComplete) {
            status = "checked_out";
        } else {
            status = "present";
        }

        return new DayEvaluation(
                status,
                graceApplied ? 0 : minutesLate,
                earlyDepartureMinutes,
                lostMinutes,
                hoursWorked,
                dayComplete,
                false,
                graceApplied
        );
    }
}
