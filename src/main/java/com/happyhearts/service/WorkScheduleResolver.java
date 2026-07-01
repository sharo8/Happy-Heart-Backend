package com.happyhearts.service;

import com.happyhearts.config.AttendanceProperties;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Employee;
import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class WorkScheduleResolver {

    private static final LocalTime FRIDAY_WORK_END = LocalTime.of(13, 30);

    private final AttendanceProperties attendanceProperties;

    public record EffectiveSchedule(
            LocalTime workStart,
            LocalTime workEnd,
            int graceMinutes,
            boolean employeeOverride
    ) {}

    public EffectiveSchedule resolve(Employee employee) {
        return resolve(employee, null);
    }

    public EffectiveSchedule resolve(Employee employee, LocalDate day) {
        Branch branch = employee.getBranch();
        LocalTime branchStart = branch != null && branch.getWorkStartTime() != null
                ? branch.getWorkStartTime()
                : attendanceProperties.getWorkStartTime();
        LocalTime branchEnd = branch != null && branch.getWorkEndTime() != null
                ? branch.getWorkEndTime()
                : attendanceProperties.getWorkEndTime();
        int branchGrace = branch != null && branch.getGracePeriodMinutes() != null
                ? branch.getGracePeriodMinutes()
                : attendanceProperties.getGracePeriodMinutes();

        EffectiveSchedule schedule;
        if (!employee.isUseBranchSchedule()) {
            LocalTime start = employee.getWorkStartTime() != null ? employee.getWorkStartTime() : branchStart;
            LocalTime end = employee.getWorkEndTime() != null ? employee.getWorkEndTime() : branchEnd;
            int grace = employee.getGracePeriodMinutes() != null
                    ? employee.getGracePeriodMinutes()
                    : branchGrace;
            schedule = new EffectiveSchedule(start, end, grace, true);
        } else {
            schedule = new EffectiveSchedule(branchStart, branchEnd, branchGrace, false);
        }
        return applyFridayEnd(schedule, day);
    }

    private EffectiveSchedule applyFridayEnd(EffectiveSchedule schedule, LocalDate day) {
        if (day == null || day.getDayOfWeek() != DayOfWeek.FRIDAY) {
            return schedule;
        }
        return new EffectiveSchedule(
                schedule.workStart(),
                FRIDAY_WORK_END,
                schedule.graceMinutes(),
                schedule.employeeOverride()
        );
    }

    public boolean isLate(Employee employee, Instant checkIn) {
        return minutesLate(employee, checkIn) > 0;
    }

    public int minutesLate(Employee employee, Instant checkIn) {
        if (checkIn == null) {
            return 0;
        }
        EffectiveSchedule schedule = resolve(employee);
        LocalTime arrival = TimeUtils.toKigaliTime(checkIn);
        LocalTime expected = schedule.workStart();
        if (!arrival.isAfter(expected)) {
            return 0;
        }
        return (int) ChronoUnit.MINUTES.between(expected, arrival);
    }

    public boolean isEarlyDeparture(Employee employee, Instant checkOut) {
        if (checkOut == null) {
            return false;
        }
        LocalDate day = TimeUtils.toKigaliDate(checkOut);
        EffectiveSchedule schedule = resolve(employee, day);
        LocalTime departure = TimeUtils.toKigaliTime(checkOut);
        return departure.isBefore(schedule.workEnd());
    }

    public int earlyDepartureMinutes(Employee employee, Instant checkOut) {
        if (!isEarlyDeparture(employee, checkOut)) {
            return 0;
        }
        LocalDate day = TimeUtils.toKigaliDate(checkOut);
        EffectiveSchedule schedule = resolve(employee, day);
        LocalTime departure = TimeUtils.toKigaliTime(checkOut);
        return (int) ChronoUnit.MINUTES.between(departure, schedule.workEnd());
    }
}
