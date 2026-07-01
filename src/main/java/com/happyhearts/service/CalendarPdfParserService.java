package com.happyhearts.service;

import com.happyhearts.dto.request.CreateCalendarEntryRequest;
import com.happyhearts.enums.CalendarEntryType;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class CalendarPdfParserService {

    private static final Pattern RANGE_PATTERN = Pattern.compile(
            "(.+?)\\s*[:\\-]\\s*(\\d{1,2})[\\./](\\d{1,2})\\s*-\\s*(\\d{1,2})[\\./](\\d{1,2})"
    );

    private static final Pattern SINGLE_PATTERN = Pattern.compile(
            "(.+?)\\s*[:\\-]\\s*(\\d{1,2})[\\./](\\d{1,2})"
    );

    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})");

    public List<CreateCalendarEntryRequest> parse(byte[] pdfBytes, int referenceYear, String preferredLanguage) {
        String text = extractText(pdfBytes);
        if (text == null || text.isBlank()) return List.of();

        int year = guessYear(text, referenceYear);
        String lang = (preferredLanguage == null || preferredLanguage.isBlank()) ? "en" : preferredLanguage.toLowerCase(Locale.ROOT);

        List<CreateCalendarEntryRequest> out = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String l = line.trim();
            if (l.isEmpty()) continue;

            // Try range first: "Event: 01.06 - 05.06"
            Matcher mRange = RANGE_PATTERN.matcher(l);
            if (mRange.find()) {
                String label = normalizeLabel(mRange.group(1));
                LocalDate s = parseDayMonth(year, mRange.group(2), mRange.group(3));
                LocalDate e = parseDayMonth(year, mRange.group(4), mRange.group(5));
                CalendarEntryType type = inferType(label);
                out.add(scopedEntry(type, label, s, e, lang));
                continue;
            }

            Matcher mSingle = SINGLE_PATTERN.matcher(l);
            if (mSingle.find()) {
                String label = normalizeLabel(mSingle.group(1));
                LocalDate d = parseDayMonth(year, mSingle.group(2), mSingle.group(3));
                CalendarEntryType type = inferType(label);
                out.add(scopedEntry(type, label, d, d, lang));
            }
        }
        return out;
    }

    private String extractText(byte[] pdfBytes) {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (IOException e) {
            return "";
        }
    }

    private int guessYear(String text, int fallbackYear) {
        Matcher m = YEAR_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {
            }
        }
        return fallbackYear;
    }

    private LocalDate parseDayMonth(int year, String dayStr, String monthStr) {
        int day = Integer.parseInt(dayStr);
        int month = Integer.parseInt(monthStr);
        return LocalDate.of(year, month, day);
    }

    private CalendarEntryType inferType(String label) {
        if (label == null) return CalendarEntryType.OPEN;
        String l = label.toLowerCase(Locale.ROOT);

        // Heuristic: closures / holidays
        if (containsAny(l,
                "holiday", "break", "closure", "independence", "liberation",
                "eid", "easter", "christmas", "vacation", "ferie", "feries",
                "public holiday", "school closure", "shutdown"
        )) {
            return CalendarEntryType.CLOSURE;
        }

        // Heuristic: modified schedule / special events
        if (containsAny(l,
                "early", "preschool", "graduation", "party", "assembly", "special schedule",
                "modified", "ceremony"
        )) {
            return CalendarEntryType.MODIFIED;
        }

        return CalendarEntryType.OPEN;
    }

    private boolean containsAny(String text, String... parts) {
        for (String p : parts) {
            if (text.contains(p)) return true;
        }
        return false;
    }

    private String normalizeLabel(String raw) {
        if (raw == null) return "";
        // Remove common "notes" suffixes present in exported PDFs.
        return raw.replaceAll("\\(.*?\\)", "").trim();
    }

    private CreateCalendarEntryRequest scopedEntry(
            CalendarEntryType type,
            String label,
            LocalDate start,
            LocalDate end,
            String preferredLanguage
    ) {
        String labelEn = null;
        String labelFr = null;
        String labelKi = null;
        if (preferredLanguage.startsWith("fr")) labelFr = label;
        else if (preferredLanguage.startsWith("ki") || preferredLanguage.startsWith("rw")) labelKi = label;
        else labelEn = label;

        // Make sure we have at least one label stored (fallback for other languages).
        if (labelEn == null && labelFr == null && labelKi == null) labelEn = label;
        if (labelEn == null && labelFr != null) labelEn = labelFr;
        if (labelEn == null && labelKi != null) labelEn = labelKi;
        return CreateCalendarEntryRequest.builder()
                .type(type)
                .startDate(start)
                .endDate(end)
                .labelEn(labelEn)
                .labelFr(labelFr)
                .labelKi(labelKi)
                .appliesToAll(false)
                .branchIds(List.of())
                .build();
    }
}

