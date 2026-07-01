package com.happyhearts.service;

import com.happyhearts.model.AttendanceRecord;
import com.happyhearts.model.Branch;
import com.happyhearts.model.Employee;
import com.happyhearts.repository.AttendanceRecordRepository;
import com.happyhearts.repository.EmployeeRepository;
import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchAnalyticsService {

    private static final int TOP_LIMIT = 10;

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceEvaluationService attendanceEvaluationService;
    private final ExpectedWorkDayService expectedWorkDayService;

    @Transactional(readOnly = true)
    public Map<String, Object> branchAnalytics(LocalDate from, LocalDate to, UUID branchIdFilter) {
        LocalDate today = LocalDate.now(TimeUtils.kigali());
        LocalDate rawStart = from != null ? from : today.minusDays(6);
        LocalDate rawEnd = to != null ? to : today;
        if (rawEnd.isAfter(today)) {
            rawEnd = today;
        }
        if (rawEnd.isBefore(rawStart)) {
            LocalDate tmp = rawStart;
            rawStart = rawEnd;
            rawEnd = tmp;
        }
        final LocalDate start = rawStart;
        final LocalDate end = rawEnd;

        int expectedWorkDays = countExpectedWorkDays(start, end, branchIdFilter);
        List<Employee> employees = branchIdFilter != null
                ? employeeRepository.findAllActiveByBranch_Id(branchIdFilter)
                : employeeRepository.findAllActiveWithBranch();

        Map<String, List<AttendanceRecord>> recordsByEmployeeDay = indexRecords(start, end, branchIdFilter);

        Map<UUID, BranchStats> byBranch = new LinkedHashMap<>();
        Map<String, RoleStats> byRole = new LinkedHashMap<>();
        Map<String, java.util.Set<UUID>> roleHeadcounts = new LinkedHashMap<>();
        Map<UUID, EmployeeStats> byEmployee = new LinkedHashMap<>();
        Map<String, TrendBucket> trends = new LinkedHashMap<>();
        GlobalStats global = new GlobalStats();

        for (Employee employee : employees) {
            Branch branch = employee.getBranch();
            if (branch == null) {
                continue;
            }
            UUID branchId = branch.getId();
            BranchStats branchStats = byBranch.computeIfAbsent(branchId, k -> new BranchStats(branch));
            String roleKey = employee.getCategory() != null ? employee.getCategory().name() : "UNKNOWN";
            RoleStats roleStats = byRole.computeIfAbsent(roleKey, k -> new RoleStats(roleKey));
            roleHeadcounts.computeIfAbsent(roleKey, k -> new java.util.HashSet<>()).add(employee.getId());
            EmployeeStats empStats = byEmployee.computeIfAbsent(employee.getId(), k -> new EmployeeStats(employee, branch));

            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                if (!isWeekday(day)) {
                    continue;
                }

                String key = employee.getId() + "|" + day;
                List<AttendanceRecord> dayLogs = recordsByEmployeeDay.getOrDefault(key, List.of());

                AttendanceEvaluationService.DayEvaluation eval =
                        attendanceEvaluationService.evaluateDay(employee, day, dayLogs);

                String status = eval.status();
                if ("off_day".equals(status)) {
                    status = "absent";
                }

                branchStats.totalEmployeeDays++;
                roleStats.totalEmployeeDays++;
                empStats.totalEmployeeDays++;
                global.totalEmployeeDays++;

                applyStatus(status, eval, branchStats, roleStats, empStats, global);

                String periodKey = trendKey(day, start, end);
                final LocalDate trendDay = day;
                TrendBucket bucket = trends.computeIfAbsent(periodKey, k -> new TrendBucket(periodKey, trendLabel(trendDay, start, end)));
                applyTrendStatus(status, bucket);
            }
        }

        List<Map<String, Object>> branches = new ArrayList<>();
        Map<String, Object> highest = new LinkedHashMap<>();
        for (BranchStats stats : byBranch.values()) {
            Map<String, Object> row = stats.toMap();
            branches.add(row);
            trackHighest(highest, row);
        }
        branches.sort(Comparator.comparing(r -> String.valueOf(r.get("branchName")), String.CASE_INSENSITIVE_ORDER));

        List<Map<String, Object>> roleRows = byRole.entrySet().stream()
                .map(e -> e.getValue().toMap(roleHeadcounts.getOrDefault(e.getKey(), java.util.Set.of()).size()))
                .sorted(Comparator.comparing(r -> String.valueOf(r.get("role"))))
                .toList();

        List<Map<String, Object>> trendRows = trends.values().stream()
                .sorted(Comparator.comparing(TrendBucket::key))
                .map(TrendBucket::toMap)
                .toList();

        List<Map<String, Object>> rankingBestAttendance = branches.stream()
                .sorted(Comparator.<Map<String, Object>, Double>comparing(r -> ((Number) r.get("presentRate")).doubleValue()).reversed())
                .limit(TOP_LIMIT)
                .toList();

        List<Map<String, Object>> rankingLeastLate = branches.stream()
                .sorted(Comparator.comparing(r -> ((Number) r.get("lateRate")).doubleValue()))
                .limit(TOP_LIMIT)
                .toList();

        List<Map<String, Object>> rankingLeastExcuses = branches.stream()
                .sorted(Comparator.comparing(r -> ((Number) r.get("excused")).intValue()))
                .limit(TOP_LIMIT)
                .toList();

        List<Map<String, Object>> topAbsent = byEmployee.values().stream()
                .filter(e -> branchIdFilter == null || branchIdFilter.equals(e.branchId))
                .sorted(Comparator.comparingInt((EmployeeStats e) -> e.absent).reversed())
                .limit(TOP_LIMIT)
                .map(EmployeeStats::toAbsentRow)
                .toList();

        Map<String, List<Map<String, Object>>> topLateByBranch = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> mostAssiduousByBranch = new LinkedHashMap<>();
        for (BranchStats bs : byBranch.values()) {
            if (branchIdFilter != null && !branchIdFilter.equals(bs.branch.getId())) {
                continue;
            }
            String bid = bs.branch.getId().toString();
            List<Map<String, Object>> lateList = byEmployee.values().stream()
                    .filter(e -> bid.equals(e.branchId.toString()))
                    .sorted(Comparator.comparingInt((EmployeeStats e) -> e.late).reversed()
                            .thenComparingInt(e -> e.minutesLate).reversed())
                    .limit(TOP_LIMIT)
                    .map(EmployeeStats::toLateRow)
                    .toList();
            topLateByBranch.put(bid, lateList);

            List<Map<String, Object>> assiduousList = byEmployee.values().stream()
                    .filter(e -> bid.equals(e.branchId.toString()))
                    .sorted(Comparator.comparingInt((EmployeeStats e) -> e.absent + e.late)
                            .thenComparingInt(e -> e.minutesLate))
                    .limit(TOP_LIMIT)
                    .map(EmployeeStats::toAssiduousRow)
                    .toList();
            mostAssiduousByBranch.put(bid, assiduousList);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", start.toString());
        result.put("to", end.toString());
        result.put("expectedWorkDays", expectedWorkDays);
        result.put("employeeCount", employees.size());
        result.put("global", global.toMap());
        result.put("branches", branches);
        result.put("highlights", highest);
        result.put("byRole", roleRows);
        result.put("trends", trendRows);
        result.put("rankings", Map.of(
                "bestAttendance", rankingBestAttendance,
                "leastLate", rankingLeastLate,
                "leastExcuses", rankingLeastExcuses
        ));
        result.put("topAbsentEmployees", topAbsent);
        result.put("topLateByBranch", topLateByBranch);
        result.put("mostAssiduousByBranch", mostAssiduousByBranch);
        List<Map<String, Object>> employeeStats = byEmployee.values().stream()
                .filter(e -> branchIdFilter == null || branchIdFilter.equals(e.branchId))
                .map(EmployeeStats::toStatsRow)
                .toList();
        result.put("employeeStats", employeeStats);
        return result;
    }

    private int countExpectedWorkDays(LocalDate start, LocalDate end, UUID branchId) {
        int count = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (isWeekday(day)) {
                if (branchId == null || expectedWorkDayService.isExpectedWorkDay(branchId, day)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isWeekday(LocalDate day) {
        DayOfWeek dow = day.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    private void applyStatus(
            String status,
            AttendanceEvaluationService.DayEvaluation eval,
            BranchStats branch,
            RoleStats role,
            EmployeeStats employee,
            GlobalStats global
    ) {
        switch (status) {
            case "present" -> {
                branch.present++;
                role.present++;
                employee.present++;
                global.present++;
            }
            case "checked_out" -> {
                branch.present++;
                role.present++;
                employee.present++;
                global.present++;
            }
            case "late" -> {
                branch.late++;
                branch.present++;
                role.late++;
                role.present++;
                employee.late++;
                employee.present++;
                employee.minutesLate += eval.minutesLate();
                global.late++;
                global.present++;
            }
            case "grace" -> {
                branch.grace++;
                branch.present++;
                role.grace++;
                role.present++;
                employee.grace++;
                employee.present++;
                global.grace++;
                global.present++;
            }
            case "excused" -> {
                branch.excused++;
                role.excused++;
                employee.excused++;
                global.excused++;
            }
            case "absent" -> {
                branch.absent++;
                role.absent++;
                employee.absent++;
                global.absent++;
            }
            case "early_departure" -> {
                branch.present++;
                branch.earlyDeparture++;
                role.present++;
                role.earlyDeparture++;
                employee.present++;
                employee.earlyDeparture++;
                global.present++;
            }
            case "off_day" -> { }
            default -> {
                branch.absent++;
                role.absent++;
                employee.absent++;
                global.absent++;
            }
        }
        branch.lostMinutes += eval.lostMinutes();
        role.lostMinutes += eval.lostMinutes();
        employee.lostMinutes += eval.lostMinutes();
        global.lostMinutes += eval.lostMinutes();
    }

    private void applyTrendStatus(String status, TrendBucket bucket) {
        switch (status) {
            case "present", "checked_out", "early_departure", "late", "grace" -> bucket.present++;
            case "excused" -> bucket.excused++;
            case "off_day" -> { }
            default -> bucket.absent++;
        }
        if ("late".equals(status) || "grace".equals(status)) {
            bucket.late++;
        }
    }

    private String trendKey(LocalDate day, LocalDate start, LocalDate end) {
        long span = ChronoUnit.DAYS.between(start, end) + 1;
        if (span > 45) {
            return day.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        if (span > 14) {
            WeekFields wf = WeekFields.of(Locale.getDefault());
            int week = day.get(wf.weekOfWeekBasedYear());
            int year = day.get(wf.weekBasedYear());
            return String.format("%d-W%02d", year, week);
        }
        return day.toString();
    }

    private String trendLabel(LocalDate day, LocalDate start, LocalDate end) {
        return trendKey(day, start, end);
    }

    private void trackHighest(Map<String, Object> highest, Map<String, Object> row) {
        track(highest, "absent", row, "absentRate");
        track(highest, "present", row, "presentRate");
        track(highest, "late", row, "lateRate");
        track(highest, "grace", row, "graceRate");
        track(highest, "excused", row, "excusedRate");
    }

    private void track(Map<String, Object> highest, String key, Map<String, Object> row, String rateKey) {
        double rate = ((Number) row.get(rateKey)).doubleValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> current = (Map<String, Object>) highest.get(key);
        if (current == null || rate > ((Number) current.get("rate")).doubleValue()) {
            highest.put(key, Map.of(
                    "branchId", row.get("branchId"),
                    "branchName", row.get("branchName"),
                    "rate", rate
            ));
        }
    }

    private Map<String, List<AttendanceRecord>> indexRecords(LocalDate start, LocalDate end, UUID branchIdFilter) {
        var rangeStart = TimeUtils.startOfDayKigali(start);
        var rangeEnd = TimeUtils.endOfDayKigali(end);
        List<AttendanceRecord> records = branchIdFilter != null
                ? attendanceRecordRepository.findInRangeForBranchWithEmployee(rangeStart, rangeEnd, branchIdFilter)
                : attendanceRecordRepository.findInRangeWithEmployee(rangeStart, rangeEnd);
        Map<String, List<AttendanceRecord>> raw = new HashMap<>();
        for (AttendanceRecord record : records) {
            Employee emp = record.getEmployee();
            String key = emp.getId() + "|" + TimeUtils.toKigaliDate(record.getScannedAt());
            raw.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }
        return raw;
    }

    private static double rate(int part, int total) {
        return total == 0 ? 0 : Math.round(part * 10000.0 / total) / 100.0;
    }

    private static class GlobalStats {
        int totalEmployeeDays;
        int present;
        int absent;
        int late;
        int grace;
        int excused;
        int lostMinutes;

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("totalEmployeeDays", totalEmployeeDays);
            m.put("present", present);
            m.put("absent", absent);
            m.put("late", late);
            m.put("grace", grace);
            m.put("excused", excused);
            m.put("presentRate", rate(present, totalEmployeeDays));
            m.put("absentRate", rate(absent, totalEmployeeDays));
            m.put("lateRate", rate(late, totalEmployeeDays));
            m.put("graceRate", rate(grace, totalEmployeeDays));
            m.put("excusedRate", rate(excused, totalEmployeeDays));
            m.put("lostHours", Math.round(lostMinutes / 60.0 * 10.0) / 10.0);
            return m;
        }
    }

    private static class BranchStats {
        private final Branch branch;
        private int totalEmployeeDays;
        private int present;
        private int absent;
        private int late;
        private int grace;
        private int excused;
        private int earlyDeparture;
        private int lostMinutes;

        BranchStats(Branch branch) {
            this.branch = branch;
        }

        Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("branchId", branch.getId().toString());
            row.put("branchCode", branch.getCode());
            row.put("branchName", branch.getName());
            row.put("displayName", branch.getName());
            row.put("totalEmployeeDays", totalEmployeeDays);
            row.put("present", present);
            row.put("absent", absent);
            row.put("late", late);
            row.put("grace", grace);
            row.put("excused", excused);
            row.put("earlyDeparture", earlyDeparture);
            row.put("presentRate", rate(present, totalEmployeeDays));
            row.put("absentRate", rate(absent, totalEmployeeDays));
            row.put("lateRate", rate(late, totalEmployeeDays));
            row.put("graceRate", rate(grace, totalEmployeeDays));
            row.put("excusedRate", rate(excused, totalEmployeeDays));
            row.put("earlyDepartureRate", rate(earlyDeparture, totalEmployeeDays));
            row.put("lostHours", Math.round(lostMinutes / 60.0 * 10.0) / 10.0);
            return row;
        }
    }

    private static class RoleStats {
        private final String role;
        private int totalEmployeeDays;
        private int present;
        private int absent;
        private int late;
        private int grace;
        private int excused;
        private int earlyDeparture;
        private int lostMinutes;

        RoleStats(String role) {
            this.role = role;
        }

        Map<String, Object> toMap(int headcount) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", role);
            row.put("headcount", headcount);
            row.put("totalEmployeeDays", totalEmployeeDays);
            row.put("presentRate", rate(present, totalEmployeeDays));
            row.put("absentRate", rate(absent, totalEmployeeDays));
            row.put("lateRate", rate(late, totalEmployeeDays));
            int punctualDays = Math.max(0, present - late - grace);
            row.put("punctualityRate", rate(punctualDays, totalEmployeeDays));
            row.put("graceRate", rate(grace, totalEmployeeDays));
            row.put("excusedRate", rate(excused, totalEmployeeDays));
            return row;
        }
    }

    private static class EmployeeStats {
        private final UUID employeeId;
        private final String fullName;
        private final UUID branchId;
        private final String branchName;
        private final String department;
        private int totalEmployeeDays;
        private int present;
        private int absent;
        private int late;
        private int grace;
        private int excused;
        private int earlyDeparture;
        private int minutesLate;
        private int lostMinutes;

        EmployeeStats(Employee employee, Branch branch) {
            this.employeeId = employee.getId();
            this.fullName = employee.getFirstName() + " " + employee.getLastName();
            this.branchId = branch.getId();
            this.branchName = branch.getName();
            this.department = employee.getCategory() != null ? employee.getCategory().name() : "";
        }

        Map<String, Object> toAbsentRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", employeeId.toString());
            row.put("fullName", fullName);
            row.put("branchId", branchId.toString());
            row.put("branchName", branchName);
            row.put("absentDays", absent);
            row.put("totalDays", totalEmployeeDays);
            return row;
        }

        Map<String, Object> toLateRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", employeeId.toString());
            row.put("fullName", fullName);
            row.put("branchName", branchName);
            row.put("lateDays", late);
            row.put("minutesLate", minutesLate);
            return row;
        }

        Map<String, Object> toAssiduousRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", employeeId.toString());
            row.put("fullName", fullName);
            row.put("branchName", branchName);
            row.put("presentDays", present);
            row.put("absentDays", absent);
            row.put("lateDays", late);
            row.put("score", absent + late);
            return row;
        }

        Map<String, Object> toStatsRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", employeeId.toString());
            row.put("fullName", fullName);
            row.put("branchId", branchId.toString());
            row.put("branchName", branchName);
            row.put("role", department);
            row.put("daysPresent", present);
            row.put("punctualDays", Math.max(0, present - late - grace));
            row.put("lateDays", late);
            row.put("absentDays", absent);
            row.put("graceDays", grace);
            row.put("totalDays", totalEmployeeDays);
            return row;
        }
    }

    private static class TrendBucket {
        private final String key;
        private final String label;
        private int present;
        private int absent;
        private int late;
        private int excused;

        TrendBucket(String key, String label) {
            this.key = key;
            this.label = label;
        }

        String key() {
            return key;
        }

        Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", label);
            row.put("present", present);
            row.put("absent", absent);
            row.put("late", late);
            row.put("excused", excused);
            return row;
        }
    }
}
