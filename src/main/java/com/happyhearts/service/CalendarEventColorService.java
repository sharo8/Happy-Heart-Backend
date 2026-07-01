package com.happyhearts.service;

import com.happyhearts.enums.EventType;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class CalendarEventColorService {

    private static final Pattern GRADUATION_TITLE = Pattern.compile(
            "(?i)(\\bgraduation\\b|\\blast\\s+day\\b|\\bparty\\b)"
    );

    private static final Map<EventType, String[]> COLORS = new LinkedHashMap<>();

    static {
        COLORS.put(EventType.TERM_HEADER, new String[]{"#F65E00", "#FFFFFF"});
        COLORS.put(EventType.WEEKEND, new String[]{"#FFFF00", "#000000"});
        COLORS.put(EventType.HOLIDAY, new String[]{"#92D050", "#000000"});
        COLORS.put(EventType.EXAM, new String[]{"#4A86E7", "#FFFFFF"});
        COLORS.put(EventType.GRADUATION, new String[]{"#FF0000", "#FFFFFF"});
        COLORS.put(EventType.OBSERVATION, new String[]{"#FF00FF", "#FFFFFF"});
        COLORS.put(EventType.TENTATIVE, new String[]{"#FF9900", "#FFFFFF"});
        COLORS.put(EventType.BREAK, new String[]{"#00FFFF", "#000000"});
        COLORS.put(EventType.SCHOOL_DAY, new String[]{"#FFFFFF", "#000000"});
    }

    public String getBg(EventType type) {
        if (type == null) {
            return COLORS.get(EventType.SCHOOL_DAY)[0];
        }
        EventType c = canonicalForColors(type);
        return COLORS.getOrDefault(c, COLORS.get(EventType.SCHOOL_DAY))[0];
    }

    public String getText(EventType type) {
        if (type == null) {
            return COLORS.get(EventType.SCHOOL_DAY)[1];
        }
        EventType c = canonicalForColors(type);
        return COLORS.getOrDefault(c, COLORS.get(EventType.SCHOOL_DAY))[1];
    }

    /** Maps legacy DB enum values to the PDF palette bucket used for colours. */
    private static EventType canonicalForColors(EventType t) {
        return switch (t) {
            case PREPARATION -> EventType.HOLIDAY;
            case CAMP -> EventType.GRADUATION;
            case EVENT -> EventType.SCHOOL_DAY;
            default -> t;
        };
    }

    /**
     * Maps a PDF-style title (+ optional tentative flag) to the PDF colour category.
     * Order follows the project's reference mapping (e.g. "Summer break" → HOLIDAY, not BREAK).
     */
    public EventType detectFromTitle(String title, boolean isTentative) {
        if (title == null || title.isBlank()) {
            return EventType.SCHOOL_DAY;
        }
        String t = title.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        if (isTentative) {
            return EventType.TENTATIVE;
        }
        if (t.contains("hhfa") || t.contains("eid")) {
            return EventType.TENTATIVE;
        }
        if (t.contains("summer break")) {
            return EventType.HOLIDAY;
        }
        if (t.contains("assumption")
                || t.contains("harvest day")
                || t.contains("next year preparation")
                || t.contains("independence day")
                || t.contains("liberation day")
                || (t.contains("labour") && t.contains("day"))
                || (t.contains("labor") && t.contains("day"))) {
            return EventType.HOLIDAY;
        }
        if (t.contains("back to school") || t.contains("april break")) {
            return EventType.BREAK;
        }
        if (t.contains("break")) {
            return EventType.BREAK;
        }
        if (GRADUATION_TITLE.matcher(t).find()) {
            return EventType.GRADUATION;
        }
        if (t.contains("report") || t.contains("exam")) {
            return EventType.EXAM;
        }
        if (t.contains("observation")) {
            return EventType.OBSERVATION;
        }
        if (t.contains("good friday")) {
            return EventType.TERM_HEADER;
        }
        return EventType.HOLIDAY;
    }
}
