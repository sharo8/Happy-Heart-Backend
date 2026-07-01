package com.happyhearts.service;

import com.happyhearts.enums.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts school calendar events from PDF text (Happy Hearts academic PDF layout).
 * PDFBox often merges several events on one physical line; {@link #EVENT_PATTERN} finds each
 * {@code Title: dd.mm} segment anywhere on the line (not only at {@code ^}).
 * <p>School year {@code referenceYear}–{@code referenceYear+1}: months 9–12 → {@code referenceYear},
 * months 1–8 → {@code referenceYear + 1}. Cross-month ranges adjust end with {@code plusYears(1)}
 * when the resolved end is before the start.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchoolYearPdfCalendarParserService {

    private final CalendarEventColorService calendarEventColorService;

    private static final Pattern PERIOD_HEADER = Pattern.compile(
            "(?i)(TERM\\s+I(?!I|V)|TERM\\s+II(?!I|V)|TERM\\s+III|SUMMER|HOLIDAY|SPRING)\\b"
    );

    /**
     * Multiple events per line; lookahead ends each match before the next {@code Title} (space + capital).
     */
    private static final Pattern EVENT_PATTERN = Pattern.compile(
            "([A-Za-z\\u00C0-\\u00FF\\u2019'][A-Za-z\\u00C0-\\u00FF\\u2019'\\s\\-\\.&]{1,60}?)"
                    + "\\s*:\\s*"
                    + "(\\d{1,2}\\.\\d{2}(?:\\s*[-\\u2013]\\s*\\d{1,2}\\.\\d{2})?)"
                    + "([^:]{0,120}?)(?=\\s+[A-Z]|\\s*$)",
            Pattern.MULTILINE
    );

    private static final Pattern DATE_RANGE_IN_BLOCK = Pattern.compile(
            "^(\\d{1,2}\\.\\d{2})\\s*[-\\u2013–]\\s*(\\d{1,2}\\.\\d{2})$"
    );

    private static final Pattern MONTH_YEAR_HEADER = Pattern.compile(
            "(?i)^[A-Za-zÀ-ÿ]+\\s+\\d{4}$"
    );

    private static final Pattern TERM_OR_SEASON_TITLE = Pattern.compile(
            "(?i)^TERM\\s+[IVX]+$|^(SUMMER|SPRING|HOLIDAY)$"
    );

    /** "Subject to change" only counts for this event if it is not the lead-in to another {@code Title: dd.mm} on the same line. */
    private static final Pattern SUBJECT_TO_CHANGE = Pattern.compile("subject to change", Pattern.CASE_INSENSITIVE);

    private static final Pattern NEXT_TITLE_DATE_ON_LINE = Pattern.compile(
            "\\s+[A-Z][A-Za-z\\u00C0-\\u00FF'\\-]{1,60}\\s*:\\s*\\d{1,2}\\.\\d{2}"
    );

    public static String schoolYearLabel(int academicYearStart) {
        return academicYearStart + "-" + (academicYearStart + 1);
    }

    public record ParsedEvent(
            String periodKey,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            EventType pdfEventType,
            boolean tentative
    ) {}

    public record ParsedCalendar(String schoolYear, List<ParsedEvent> events, String rawText) {}

    public ParsedCalendar parse(byte[] pdfBytes, int referenceYear) throws IOException {
        String text = extractText(pdfBytes);
        if (text == null || text.isBlank()) {
            return new ParsedCalendar(schoolYearLabel(referenceYear), List.of(), "");
        }

        log.info("=== TEXTE BRUT PDF (100 premiers chars) : {}",
                text.substring(0, Math.min(100, text.length())));

        String schoolYear = schoolYearLabel(referenceYear);
        List<ParsedEvent> events = new ArrayList<>();
        String currentPeriod = "GENERAL";

        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (PERIOD_HEADER.matcher(line).find()) {
                currentPeriod = normalizePeriodName(line);
                continue;
            }

            line = line.replaceFirst("^\\d{1,2}\\.\\s+(?=\\p{L})", "");
            line = line.replaceFirst("^(?:[-–—•]\\s*)+", "");

            Matcher m = EVENT_PATTERN.matcher(line);
            while (m.find()) {
                String rawTitle = m.group(1).trim();
                String dateBlock = m.group(2).trim();
                String rest = m.group(3) != null ? m.group(3).trim() : "";

                log.info("EVENT TROUVÉ : titre=[{}] dates=[{}] reste=[{}]",
                        rawTitle, dateBlock, rest);

                if (rawTitle.length() < 3) {
                    continue;
                }
                if (rawTitle.matches("[MTWTHFSSmtwthfs\\s\\d]+")) {
                    continue;
                }
                if (MONTH_YEAR_HEADER.matcher(rawTitle).matches()) {
                    continue;
                }
                if (TERM_OR_SEASON_TITLE.matcher(rawTitle).matches()) {
                    continue;
                }
                if (rawTitle.length() > 90) {
                    continue;
                }
                if (rawTitle.toLowerCase(Locale.ROOT).contains("reference year")) {
                    continue;
                }

                String[] dm = splitDateBlock(dateBlock);
                if (dm == null) {
                    continue;
                }
                String dm1 = dm[0];
                String dm2 = dm[1];

                LocalDate[] range = parseDateRange(dm1, dm2, referenceYear);
                if (range == null) {
                    continue;
                }

                boolean isTentative = tentativeForThisEventRest(rest);
                String cleanTitle = stripTitleParens(rawTitle);
                if (cleanTitle.isBlank()) {
                    continue;
                }

                EventType pdfType = calendarEventColorService.detectFromTitle(cleanTitle, isTentative);
                events.add(new ParsedEvent(
                        currentPeriod,
                        cleanTitle,
                        range[0],
                        range[1],
                        pdfType,
                        isTentative
                ));
            }
        }

        log.info("=== TOTAL EVENTS PARSÉS : {} ===", events.size());
        log.info("Parsed {} school-calendar events from PDF", events.size());

        if (log.isDebugEnabled()) {
            log.debug("=== TEXTE BRUT EXTRAIT DU PDF (complet) ===");
            log.debug(text);
            log.debug("=== FIN TEXTE BRUT ===");
            for (ParsedEvent e : events) {
                log.debug("  → {} | {} → {} | pdfType={}",
                        e.title(), e.startDate(), e.endDate(), e.pdfEventType());
            }
        }

        return new ParsedCalendar(schoolYear, events, text);
    }

    private static String[] splitDateBlock(String dateBlock) {
        if (dateBlock == null || dateBlock.isBlank()) {
            return null;
        }
        String t = dateBlock.trim();
        Matcher rm = DATE_RANGE_IN_BLOCK.matcher(t);
        if (rm.matches()) {
            return new String[]{rm.group(1), rm.group(2)};
        }
        if (t.matches("^\\d{1,2}\\.\\d{2}$")) {
            return new String[]{t, null};
        }
        return null;
    }

    private String normalizePeriodName(String headerLine) {
        String u = headerLine.toUpperCase(Locale.ROOT);
        if (u.contains("TERM III")) {
            return "TERM III";
        }
        if (u.contains("TERM II")) {
            return "TERM II";
        }
        if (u.contains("TERM I")) {
            return "TERM I";
        }
        if (u.contains("SUMMER")) {
            return "SUMMER";
        }
        if (u.contains("HOLIDAY")) {
            return "HOLIDAY";
        }
        if (u.contains("SPRING")) {
            return "SPRING";
        }
        return headerLine.trim();
    }

    /**
     * Calendrier scolaire : sept–déc = {@code referenceYear}, jan–août = {@code referenceYear + 1}.
     */
    private int resolveYear(int month, int referenceYear) {
        return (month >= 9) ? referenceYear : referenceYear + 1;
    }

    private LocalDate resolveDate(int day, int month, int referenceYear) {
        return LocalDate.of(resolveYear(month, referenceYear), month, day);
    }

    /** Returns [start, end] inclusive; end may equal start. */
    private LocalDate[] parseDateRange(String dm1, String dm2, int referenceYear) {
        try {
            String[] p1 = dm1.split("\\.");
            if (p1.length != 2) {
                return null;
            }
            int day1 = Integer.parseInt(p1[0]);
            int mo1 = Integer.parseInt(p1[1]);
            LocalDate start = resolveDate(day1, mo1, referenceYear);
            if (dm2 == null || dm2.isBlank()) {
                return new LocalDate[]{start, start};
            }
            String[] p2 = dm2.split("\\.");
            if (p2.length != 2) {
                return null;
            }
            int day2 = Integer.parseInt(p2[0]);
            int mo2 = Integer.parseInt(p2[1]);
            LocalDate end = resolveDate(day2, mo2, referenceYear);
            if (end.isBefore(start)) {
                end = end.plusYears(1);
            }
            return new LocalDate[]{start, end};
        } catch (Exception ex) {
            return null;
        }
    }

    private String stripTitleParens(String raw) {
        return raw.replaceAll("\\(.*?\\)", "").trim();
    }

    /**
     * True only when "subject to change" appears in the text after this event's dates and is not
     * immediately followed by another {@code Title: dd.mm} segment (which belongs to the next event).
     */
    private static boolean tentativeForThisEventRest(String rest) {
        if (rest == null || rest.isBlank()) {
            return false;
        }
        Matcher stc = SUBJECT_TO_CHANGE.matcher(rest);
        if (!stc.find()) {
            return false;
        }
        String after = rest.substring(stc.end());
        Matcher nextEv = NEXT_TITLE_DATE_ON_LINE.matcher(after);
        if (nextEv.find() && nextEv.start() <= 2) {
            return false;
        }
        return true;
    }

    private String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    public LocalDate periodBoundsMin(List<ParsedEvent> events) {
        return events.stream().map(ParsedEvent::startDate).min(Comparator.naturalOrder()).orElse(null);
    }

    public LocalDate periodBoundsMax(List<ParsedEvent> events) {
        return events.stream()
                .map(e -> e.endDate().isBefore(e.startDate()) ? e.startDate() : e.endDate())
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
