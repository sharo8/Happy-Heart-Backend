package com.happyhearts.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class CalendarEventColorMapper {

    private static final Map<String, String[]> COLORS = Map.of(
            "HOLIDAY", new String[]{"#FEF3C7", "#B45309"},
            "BREAK", new String[]{"#FED7D7", "#B91C1C"},
            "GRADUATION", new String[]{"#EDE9FE", "#6D28D9"},
            "CAMP", new String[]{"#DBEAFE", "#1D4ED8"},
            "EXAM", new String[]{"#FFEDD5", "#C2410C"},
            "PREPARATION", new String[]{"#F3F4F6", "#6B7280"},
            "EVENT", new String[]{"#DCFCE7", "#15803D"}
    );

    public String[] getColors(String eventType) {
        return COLORS.getOrDefault(eventType, COLORS.get("EVENT"));
    }

    public String detectType(String title) {
        String t = title.toLowerCase(Locale.ROOT);
        if (t.contains("break") || t.contains("vacation")) {
            return "BREAK";
        }
        if (t.contains("graduation") || t.contains("party")) {
            return "GRADUATION";
        }
        if (t.contains("camp")) {
            return "CAMP";
        }
        if (t.contains("report") || t.contains("exam")) {
            return "EXAM";
        }
        if (t.contains("preparation")) {
            return "PREPARATION";
        }
        if (t.contains("observation") || t.contains("hhfa")) {
            return "EVENT";
        }
        if (t.contains("day") || t.contains("friday") || t.contains("eid") || t.contains("independence")
                || t.contains("liberation") || t.contains("harvest") || t.contains("assumption")
                || t.contains("labour") || t.contains("labor")) {
            return "HOLIDAY";
        }
        return "EVENT";
    }
}
