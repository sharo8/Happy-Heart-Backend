package com.happyhearts.service;

import com.happyhearts.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceAiContextService {

    private final Esp32AttendanceService esp32AttendanceService;
    private final BranchAnalyticsService branchAnalyticsService;

    @Transactional(readOnly = true)
    public Map<String, Object> buildAttendanceContext() {
        Map<String, Object> today = esp32AttendanceService.getTodaySummary();
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) today.get("stats");

        LocalDate end = LocalDate.now(TimeUtils.kigali());
        LocalDate start = end.minusDays(29);
        Map<String, Object> analytics = branchAnalyticsService.branchAnalytics(start, end, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> employeeStats = (List<Map<String, Object>>) analytics.getOrDefault("employeeStats", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) analytics.getOrDefault("branches", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topAbsent = (List<Map<String, Object>>) analytics.getOrDefault("topAbsentEmployees", List.of());

        List<Map<String, Object>> byPresence = employeeStats.stream()
                .sorted(Comparator.<Map<String, Object>, Double>comparing(this::presenceRate).reversed())
                .limit(10)
                .toList();

        List<Map<String, Object>> byAbsence = employeeStats.stream()
                .sorted(Comparator.<Map<String, Object>, Integer>comparing(r ->
                        ((Number) r.getOrDefault("absentDays", 0)).intValue()).reversed())
                .limit(10)
                .toList();

        List<Map<String, Object>> topLate = employeeStats.stream()
                .sorted(Comparator.<Map<String, Object>, Integer>comparing(r ->
                        ((Number) r.getOrDefault("lateDays", 0)).intValue()).reversed())
                .limit(3)
                .toList();

        List<Map<String, Object>> branchPresence = branches.stream()
                .map(b -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("branch", b.get("branchName"));
                    row.put("presentRate", b.get("presentRate"));
                    row.put("lateRate", b.get("lateRate"));
                    row.put("absent", b.get("absent"));
                    return row;
                })
                .toList();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("date", end.toString());
        context.put("period", Map.of("from", start.toString(), "to", end.toString()));
        context.put("definitions", Map.of(
                "daysPresent", "Jours où l'employé s'est présenté sur site (inclut présent à l'heure, en retard, grâce, départ anticipé).",
                "lateDays", "Jours de présence avec arrivée en retard — un retard est une PRÉSENCE, pas une absence.",
                "absentDays", "Jours ouvrés attendus sans aucun scan d'entrée.",
                "punctualDays", "Jours présents sans retard ni période de grâce.",
                "graceDays", "Jours avec période de grâce approuvée (présent, retard excusé)."
        ));
        context.put("todaySummary", Map.of(
                "present", stats.getOrDefault("onSite", stats.get("present")),
                "leftSite", stats.getOrDefault("leftSite", 0),
                "absent", stats.getOrDefault("absent", 0),
                "late", stats.getOrDefault("late", 0),
                "totalCheckOuts", stats.getOrDefault("totalCheckOuts", 0),
                "totalEmployees", stats.getOrDefault("total", 0)
        ));
        context.put("presenceRanking30Days", byPresence);
        context.put("absenceRanking30Days", byAbsence);
        context.put("branchPresenceRates", branchPresence);
        context.put("top3HabitualLate", topLate);
        context.put("topAbsentEmployees", topAbsent);
        return context;
    }

    private double presenceRate(Map<String, Object> row) {
        int total = ((Number) row.getOrDefault("totalDays", 0)).intValue();
        int present = ((Number) row.getOrDefault("daysPresent", 0)).intValue();
        return total > 0 ? (double) present / total : 0;
    }
}
