package com.happyhearts.service;

import com.happyhearts.model.CalendarDay;
import com.happyhearts.model.CalendarEvent;
import com.happyhearts.repository.CalendarDayRepository;
import com.happyhearts.repository.CalendarEventRepository;
import com.happyhearts.util.CalendarExportContrast;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Landscape PDF aligned with the school calendar Excel export (Term III + Summer).
 */
@Service
@RequiredArgsConstructor
public class CalendarPdfExportService {

    private static final int COLS_PER_MONTH = 8;
    private static final DateTimeFormatter EV_DATE = DateTimeFormatter.ofPattern("dd.MM");

    private static final Color C_TERM_BANNER = hexToColor("F65E00");
    private static final Color C_MONTH_HEADER = hexToColor("FF9900");
    private static final Color C_YELLOW = hexToColor("FFFF00");
    private static final Color C_WHITE = hexToColor("FFFFFF");
    private static final Color C_BLACK = hexToColor("000000");

    private final CalendarEventRepository eventRepo;
    private final CalendarDayRepository dayRepo;

    public void generate(String schoolYear, UUID branchId, OutputStream out) throws IOException, DocumentException {
        int gridYear = parseSpringSummerYear(schoolYear);
        List<CalendarEvent> events = eventRepo.findForExportBySchoolYear(schoolYear, branchId);
        List<CalendarDay> days = dayRepo.findForExportBySchoolYear(schoolYear, branchId);
        Map<LocalDate, String> dateColorMap = buildDateColorMap(days);

        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font titleFont = new Font(Font.HELVETICA, 12, Font.BOLD, Color.DARK_GRAY);
        doc.add(new Paragraph("Happy Hearts — " + schoolYear, titleFont));
        doc.add(new Paragraph(" "));

        List<MonthBlock> term3 = List.of(
                new MonthBlock(gridYear, Month.APRIL),
                new MonthBlock(gridYear, Month.MAY),
                new MonthBlock(gridYear, Month.JUNE));
        doc.add(buildSection("TERM III", term3, dateColorMap, events, List.of(Month.APRIL, Month.MAY, Month.JUNE), gridYear));
        doc.add(new Paragraph(" "));

        List<MonthBlock> summer = List.of(
                new MonthBlock(gridYear, Month.JULY),
                new MonthBlock(gridYear, Month.AUGUST));
        doc.add(buildSection("SUMMER", summer, dateColorMap, events, List.of(Month.JULY, Month.AUGUST), gridYear));
        doc.add(new Paragraph(" "));
        doc.add(buildLegendTable());

        doc.close();
    }

    private PdfPTable buildSection(
            String termTitle,
            List<MonthBlock> months,
            Map<LocalDate, String> dateColorMap,
            List<CalendarEvent> events,
            List<Month> eventMonths,
            int gridYear
    ) throws DocumentException {
        int cols = months.size() * COLS_PER_MONTH;
        float[] w = new float[cols];
        java.util.Arrays.fill(w, 1f);
        PdfPTable outer = new PdfPTable(cols);
        outer.setWidthPercentage(100);
        outer.setWidths(w);
        outer.setSpacingAfter(8f);

        PdfPCell term = cell(termTitle, font(14, Font.BOLD, Color.WHITE), C_TERM_BANNER, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE);
        term.setColspan(cols);
        term.setMinimumHeight(22f);
        outer.addCell(term);

        for (MonthBlock mb : months) {
            PdfPCell mh = cell(monthTitle(mb), font(11, Font.BOLD, Color.WHITE), C_MONTH_HEADER, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE);
            mh.setColspan(7);
            mh.setMinimumHeight(18f);
            outer.addCell(mh);
            outer.addCell(emptyGapCell());
        }

        String[] headers = {"M", "T", "W", "TH", "F", "S", "S"};
        for (int m = 0; m < months.size(); m++) {
            for (String h : headers) {
                outer.addCell(cell(h, font(9, Font.BOLD, Color.BLACK), C_WHITE, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE));
            }
            outer.addCell(emptyGapCell());
        }

        List<List<List<LocalDate>>> allWeeks = new ArrayList<>();
        int maxWeeks = 0;
        for (MonthBlock mb : months) {
            List<List<LocalDate>> wk = weeksForMonth(mb.year(), mb.month());
            allWeeks.add(wk);
            maxWeeks = Math.max(maxWeeks, wk.size());
        }

        for (int wi = 0; wi < maxWeeks; wi++) {
            for (int mi = 0; mi < months.size(); mi++) {
                MonthBlock mb = months.get(mi);
                List<List<LocalDate>> weeks = allWeeks.get(mi);
                if (wi < weeks.size()) {
                    List<LocalDate> week = weeks.get(wi);
                    for (int d = 0; d < 7; d++) {
                        LocalDate date = week.get(d);
                        boolean weekend = d >= 5;
                        if (date != null && date.getMonth() == mb.month() && date.getYear() == mb.year()) {
                            String hex = dateColorMap.getOrDefault(date, "#FFFFFF");
                            Color bg = weekend ? C_YELLOW : hexToColor(hex.replace("#", ""));
                            Color fg = pickPdfFg(bg);
                            PdfPCell dc = cell(String.valueOf(date.getDayOfMonth()), font(9, Font.NORMAL, fg), bg, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE);
                            dc.setMinimumHeight(16f);
                            outer.addCell(dc);
                        } else {
                            outer.addCell(cell("", font(9, Font.NORMAL, Color.BLACK), C_WHITE, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE));
                        }
                    }
                } else {
                    for (int d = 0; d < 7; d++) {
                        outer.addCell(cell("", font(9, Font.NORMAL, Color.BLACK), C_WHITE, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE));
                    }
                }
                outer.addCell(emptyGapCell());
            }
        }

        Map<Month, List<CalendarEvent>> byMonth = groupEventsByMonth(events, eventMonths, gridYear);
        int maxRows = eventMonths.stream().mapToInt(m -> byMonth.get(m).size()).max().orElse(0);
        for (int r = 0; r < maxRows; r++) {
            for (Month m : eventMonths) {
                List<CalendarEvent> evs = byMonth.get(m);
                if (r < evs.size()) {
                    CalendarEvent ev = evs.get(r);
                    Color bg = hexToColor(safeHexBg(ev));
                    Color fg = pickPdfFg(bg);
                    PdfPCell ec = cell(eventLabel(ev), font(8, Font.NORMAL, fg), bg, Element.ALIGN_LEFT, Element.ALIGN_MIDDLE);
                    ec.setColspan(7);
                    ec.setMinimumHeight(16f);
                    ec.setPaddingLeft(6f);
                    outer.addCell(ec);
                } else {
                    PdfPCell blank = cell("", font(8, Font.NORMAL, Color.BLACK), C_WHITE, Element.ALIGN_LEFT, Element.ALIGN_MIDDLE);
                    blank.setColspan(7);
                    outer.addCell(blank);
                }
                outer.addCell(emptyGapCell());
            }
        }

        return outer;
    }

    private PdfPTable buildLegendTable() throws DocumentException {
        String[][] legend = {
                {"F65E00", "Term banner"},
                {"FF9900", "Month header"},
                {"FFFF00", "Weekend"},
                {"92D050", "Holiday / break"},
                {"4472C4", "Child reports / exam"},
                {"FF0000", "Graduation"},
                {"FF00FF", "Observation"},
                {"FF6B35", "HHFA / tentative"},
                {"00FFFF", "Back to school"},
        };
        PdfPTable t = new PdfPTable(9);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6f);
        float[] lw = new float[9];
        java.util.Arrays.fill(lw, 1f);
        t.setWidths(lw);
        for (String[] row : legend) {
            Color bg = hexToColor(row[0]);
            Color fg = pickPdfFg(bg);
            PdfPCell c = cell(row[1], font(7, Font.BOLD, fg), bg, Element.ALIGN_CENTER, Element.ALIGN_MIDDLE);
            c.setMinimumHeight(14f);
            t.addCell(c);
        }
        return t;
    }

    private static PdfPCell emptyGapCell() {
        PdfPCell c = new PdfPCell(new Phrase(""));
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static PdfPCell cell(String text, Font font, Color bg, int hAlign, int vAlign) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(hAlign);
        c.setVerticalAlignment(vAlign);
        c.setBorder(Rectangle.BOX);
        c.setBorderWidth(0.5f);
        c.setBorderColor(C_BLACK);
        return c;
    }

    private static Font font(float size, int style, Color color) {
        return new Font(Font.HELVETICA, size, style, color);
    }

    private static Color pickPdfFg(Color bg) {
        byte[] rgb = new byte[]{(byte) bg.getRed(), (byte) bg.getGreen(), (byte) bg.getBlue()};
        return CalendarExportContrast.shouldUseWhiteText(rgb) ? Color.WHITE : Color.BLACK;
    }

    private static Map<Month, List<CalendarEvent>> groupEventsByMonth(
            List<CalendarEvent> events,
            List<Month> months,
            int gridYear
    ) {
        Map<Month, List<CalendarEvent>> byMonth = new HashMap<>();
        for (Month m : months) {
            byMonth.put(m, new ArrayList<>());
        }
        for (CalendarEvent e : events) {
            for (Month m : months) {
                if (overlapsMonth(e, gridYear, m)) {
                    byMonth.get(m).add(e);
                }
            }
        }
        for (Month m : months) {
            byMonth.get(m).sort(Comparator.comparing(CalendarEvent::getStartDate).thenComparing(CalendarEvent::getId));
        }
        return byMonth;
    }

    private Map<LocalDate, String> buildDateColorMap(List<CalendarDay> days) {
        Map<LocalDate, String> map = new HashMap<>();
        for (CalendarDay d : days) {
            String c = d.getColorBg();
            if (c == null || c.isBlank()) {
                continue;
            }
            String norm = c.startsWith("#") ? c : "#" + c;
            if ("#4A86E7".equalsIgnoreCase(norm)) {
                norm = "#4472C4";
            }
            map.put(d.getDayDate(), norm);
        }
        return map;
    }

    private int parseSpringSummerYear(String schoolYear) {
        if (schoolYear == null || !schoolYear.contains("-")) {
            return LocalDate.now().getYear();
        }
        String[] p = schoolYear.split("-", 2);
        try {
            return Integer.parseInt(p[1].trim());
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            try {
                return Integer.parseInt(p[0].trim()) + 1;
            } catch (NumberFormatException e2) {
                return LocalDate.now().getYear();
            }
        }
    }

    private static String monthTitle(MonthBlock mb) {
        return mb.month().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT)
                + " " + mb.year();
    }

    private List<List<LocalDate>> weeksForMonth(int year, Month month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate cur = first.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        List<List<LocalDate>> weeks = new ArrayList<>();
        while (!cur.isAfter(last)) {
            List<LocalDate> wk = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                wk.add(cur.plusDays(i));
            }
            weeks.add(wk);
            cur = cur.plusWeeks(1);
        }
        return weeks;
    }

    private static boolean overlapsMonth(CalendarEvent e, int year, Month month) {
        LocalDate s = e.getStartDate();
        LocalDate en = e.getEndDate() != null ? e.getEndDate() : e.getStartDate();
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.with(TemporalAdjusters.lastDayOfMonth());
        return !s.isAfter(last) && !en.isBefore(first);
    }

    private static String eventLabel(CalendarEvent ev) {
        StringBuilder sb = new StringBuilder();
        sb.append(ev.getTitle()).append(": ").append(ev.getStartDate().format(EV_DATE));
        if (ev.getEndDate() != null && !ev.getEndDate().equals(ev.getStartDate())) {
            sb.append(" - ").append(ev.getEndDate().format(EV_DATE));
        }
        return sb.toString();
    }

    private static String safeHexBg(CalendarEvent e) {
        String c = e.getColorBg();
        if (c == null || c.isBlank()) {
            return "FFFFFF";
        }
        String h = c.startsWith("#") ? c.substring(1) : c;
        if ("4A86E7".equalsIgnoreCase(h)) {
            return "4472C4";
        }
        return h;
    }

    private static Color hexToColor(String hex6) {
        if (hex6 == null || hex6.length() < 6) {
            return C_WHITE;
        }
        String h = hex6.startsWith("#") ? hex6.substring(1) : hex6;
        try {
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            return new Color(r, g, b);
        } catch (RuntimeException e) {
            return C_WHITE;
        }
    }

    private record MonthBlock(int year, Month month) {}
}
