package com.happyhearts.service;

import com.happyhearts.model.CalendarDay;
import com.happyhearts.model.CalendarEvent;
import com.happyhearts.repository.CalendarDayRepository;
import com.happyhearts.repository.CalendarEventRepository;
import com.happyhearts.util.CalendarExportContrast;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

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
 * Excel workbook styled like the academic PDF: Term III (Apr–Jun) + Summer (Jul–Aug) for the
 * spring/summer calendar year (second year in {@code YYYY-YYYY+1} school-year label).
 */
@Service
@RequiredArgsConstructor
public class CalendarExcelExportService {

    private static final int COLS_PER_MONTH = 8;
    private static final DateTimeFormatter EV_DATE = DateTimeFormatter.ofPattern("dd.MM");

    private static final byte[] C_TERM_BANNER = rgbBytes("F65E00");
    private static final byte[] C_MONTH_HEADER = rgbBytes("FF9900");
    private static final byte[] C_YELLOW = rgbBytes("FFFF00");
    private static final byte[] C_GREEN = rgbBytes("92D050");
    private static final byte[] C_BLUE_EXCEL = rgbBytes("4472C4");
    private static final byte[] C_RED = rgbBytes("FF0000");
    private static final byte[] C_MAGENTA = rgbBytes("FF00FF");
    private static final byte[] C_ORANGE_HHFA = rgbBytes("FF6B35");
    private static final byte[] C_CYAN = rgbBytes("00FFFF");
    private static final byte[] C_WHITE = rgbBytes("FFFFFF");
    private static final byte[] C_BLACK = rgbBytes("000000");

    private final CalendarEventRepository eventRepo;
    private final CalendarDayRepository dayRepo;

    public void generate(String schoolYear, UUID branchId, OutputStream out) throws IOException {
        int gridYear = parseSpringSummerYear(schoolYear);
        List<CalendarEvent> events = eventRepo.findForExportBySchoolYear(schoolYear, branchId);
        List<CalendarDay> days = dayRepo.findForExportBySchoolYear(schoolYear, branchId);
        Map<LocalDate, String> dateColorMap = buildDateColorMap(days);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sh = wb.createSheet("Calendar");

            int row = 0;
            List<MonthBlock> term3 = List.of(
                    new MonthBlock(gridYear, Month.APRIL),
                    new MonthBlock(gridYear, Month.MAY),
                    new MonthBlock(gridYear, Month.JUNE));
            row = writeTermBanner(wb, sh, row, "TERM III", term3);
            row = writeDayHeaders(wb, sh, row, term3.size());
            row = writeDaysGrid(wb, sh, row, term3, dateColorMap);
            row = writeEventsList(wb, sh, row, events, List.of(Month.APRIL, Month.MAY, Month.JUNE), gridYear);

            row += 2;
            List<MonthBlock> summer = List.of(
                    new MonthBlock(gridYear, Month.JULY),
                    new MonthBlock(gridYear, Month.AUGUST));
            row = writeTermBanner(wb, sh, row, "SUMMER", summer);
            row = writeDayHeaders(wb, sh, row, summer.size());
            row = writeDaysGrid(wb, sh, row, summer, dateColorMap);
            row = writeEventsList(wb, sh, row, events, List.of(Month.JULY, Month.AUGUST), gridYear);

            row += 2;
            writeLegend(wb, sh, row);

            for (int i = 0; i < summer.size() * COLS_PER_MONTH + 4; i++) {
                sh.setColumnWidth(i, 4 * 256);
            }

            wb.write(out);
        }
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

    /**
     * Second segment of {@code start-end} school year label → civil year for Apr–Aug grid.
     */
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

    private int writeTermBanner(XSSFWorkbook wb, XSSFSheet sh, int row, String term, List<MonthBlock> months) {
        int lastCol = months.size() * COLS_PER_MONTH - 1;
        XSSFRow termRow = sh.createRow(row++);
        termRow.setHeightInPoints(22);
        XSSFCell termCell = termRow.createCell(0);
        termCell.setCellValue(term);
        termCell.setCellStyle(makeStyle(wb, C_TERM_BANNER, 14, true, true, HorizontalAlignment.CENTER));
        sh.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 0, lastCol));

        XSSFRow monthRow = sh.createRow(row++);
        monthRow.setHeightInPoints(18);
        int col = 0;
        for (MonthBlock mb : months) {
            XSSFCell mc = monthRow.createCell(col);
            mc.setCellValue(monthTitle(mb));
            mc.setCellStyle(makeStyle(wb, C_MONTH_HEADER, 11, true, true, HorizontalAlignment.CENTER));
            sh.addMergedRegion(new CellRangeAddress(row - 1, row - 1, col, col + 6));
            col += COLS_PER_MONTH;
        }
        return row;
    }

    private static String monthTitle(MonthBlock mb) {
        return mb.month().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT)
                + " " + mb.year();
    }

    private int writeDayHeaders(XSSFWorkbook wb, XSSFSheet sh, int row, int monthCount) {
        String[] headers = {"M", "T", "W", "TH", "F", "S", "S"};
        XSSFRow hRow = sh.createRow(row++);
        hRow.setHeightInPoints(16);
        int col = 0;
        for (int m = 0; m < monthCount; m++) {
            for (int h = 0; h < 7; h++) {
                XSSFCell c = hRow.createCell(col + h);
                c.setCellValue(headers[h]);
                c.setCellStyle(makeStyle(wb, C_WHITE, 9, true, false, HorizontalAlignment.CENTER));
            }
            XSSFCell gap = hRow.createCell(col + 7);
            gap.setCellStyle(makeStyle(wb, C_WHITE, 9, false, false, HorizontalAlignment.CENTER));
            col += COLS_PER_MONTH;
        }
        return row;
    }

    private int writeDaysGrid(
            XSSFWorkbook wb,
            XSSFSheet sh,
            int startRow,
            List<MonthBlock> months,
            Map<LocalDate, String> colorMap
    ) {
        List<List<List<LocalDate>>> allWeeks = new ArrayList<>();
        int maxWeeks = 0;
        for (MonthBlock mb : months) {
            List<List<LocalDate>> wk = weeksForMonth(mb.year(), mb.month());
            allWeeks.add(wk);
            maxWeeks = Math.max(maxWeeks, wk.size());
        }

        int row = startRow;
        for (int w = 0; w < maxWeeks; w++) {
            XSSFRow dRow = sh.createRow(row++);
            dRow.setHeightInPoints(16);
            int col = 0;
            for (int mi = 0; mi < months.size(); mi++) {
                MonthBlock mb = months.get(mi);
                List<List<LocalDate>> weeks = allWeeks.get(mi);
                if (w < weeks.size()) {
                    List<LocalDate> week = weeks.get(w);
                    for (int d = 0; d < 7; d++) {
                        XSSFCell c = dRow.createCell(col + d);
                        LocalDate date = week.get(d);
                        boolean weekend = d >= 5;
                        if (date != null && date.getMonth() == mb.month() && date.getYear() == mb.year()) {
                            c.setCellValue(date.getDayOfMonth());
                            String hex = colorMap.getOrDefault(date, "#FFFFFF");
                            byte[] bg = weekend ? C_YELLOW : rgbBytes(hex.replace("#", ""));
                            c.setCellStyle(makeStyle(wb, bg, 9, false, false, HorizontalAlignment.CENTER));
                        } else {
                            c.setCellStyle(makeStyle(wb, C_WHITE, 9, false, false, HorizontalAlignment.CENTER));
                        }
                    }
                } else {
                    for (int d = 0; d < 7; d++) {
                        dRow.createCell(col + d).setCellStyle(makeStyle(wb, C_WHITE, 9, false, false, HorizontalAlignment.CENTER));
                    }
                }
                dRow.createCell(col + 7).setCellStyle(makeStyle(wb, C_WHITE, 9, false, false, HorizontalAlignment.CENTER));
                col += COLS_PER_MONTH;
            }
        }
        return row;
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

    private int writeEventsList(
            XSSFWorkbook wb,
            XSSFSheet sh,
            int row,
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

        int maxRows = months.stream().mapToInt(m -> byMonth.get(m).size()).max().orElse(0);
        row++;
        for (int r = 0; r < maxRows; r++) {
            XSSFRow eRow = sh.createRow(row++);
            eRow.setHeightInPoints(14);
            int col = 0;
            for (Month m : months) {
                List<CalendarEvent> evs = byMonth.get(m);
                if (r < evs.size()) {
                    CalendarEvent ev = evs.get(r);
                    String label = eventLabel(ev);
                    XSSFCell c = eRow.createCell(col);
                    c.setCellValue(label);
                    byte[] bg = rgbBytes(safeHexBg(ev));
                    c.setCellStyle(makeStyle(wb, bg, 9, false, false, HorizontalAlignment.LEFT));
                    sh.addMergedRegion(new CellRangeAddress(row - 1, row - 1, col, col + 6));
                } else {
                    for (int k = 0; k < 7; k++) {
                        eRow.createCell(col + k).setCellStyle(makeStyle(wb, C_WHITE, 9, false, false, HorizontalAlignment.CENTER));
                    }
                }
                eRow.createCell(col + 7).setCellStyle(makeStyle(wb, C_WHITE, 9, false, false, HorizontalAlignment.CENTER));
                col += COLS_PER_MONTH;
            }
        }
        return row;
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

    private void writeLegend(XSSFWorkbook wb, XSSFSheet sh, int row) {
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
        XSSFRow lRow = sh.createRow(row);
        lRow.setHeightInPoints(16);
        int col = 0;
        for (String[] entry : legend) {
            byte[] bg = rgbBytes(entry[0]);
            XSSFCell c = lRow.createCell(col);
            c.setCellValue(entry[1]);
            c.setCellStyle(makeStyle(wb, bg, 8, true, false, HorizontalAlignment.CENTER));
            sh.addMergedRegion(new CellRangeAddress(row, row, col, col + 2));
            col += 3;
        }
    }

    private XSSFCellStyle makeStyle(
            XSSFWorkbook wb,
            byte[] bgRgb,
            int fontSize,
            boolean bold,
            boolean forceWhiteForeground,
            HorizontalAlignment horizontalAlignment
    ) {
        boolean whiteFont = forceWhiteForeground || CalendarExportContrast.shouldUseWhiteText(bgRgb);
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(bgRgb, new DefaultIndexedColorMap()));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(horizontalAlignment);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(horizontalAlignment == HorizontalAlignment.LEFT);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        XSSFColor borderBlack = new XSSFColor(C_BLACK, new DefaultIndexedColorMap());
        s.setBottomBorderColor(borderBlack);
        s.setTopBorderColor(borderBlack);
        s.setLeftBorderColor(borderBlack);
        s.setRightBorderColor(borderBlack);
        XSSFFont font = wb.createFont();
        font.setFontName("Arial");
        font.setBold(bold);
        font.setFontHeightInPoints((short) fontSize);
        if (whiteFont) {
            font.setColor(IndexedColors.WHITE.getIndex());
        } else {
            font.setColor(IndexedColors.BLACK.getIndex());
        }
        s.setFont(font);
        return s;
    }

    private static byte[] rgbBytes(String hex6) {
        if (hex6 == null || hex6.length() < 6) {
            return C_WHITE.clone();
        }
        String h = hex6.startsWith("#") ? hex6.substring(1) : hex6;
        try {
            return new byte[]{
                    (byte) Integer.parseInt(h.substring(0, 2), 16),
                    (byte) Integer.parseInt(h.substring(2, 4), 16),
                    (byte) Integer.parseInt(h.substring(4, 6), 16)
            };
        } catch (RuntimeException e) {
            return C_WHITE.clone();
        }
    }

    private record MonthBlock(int year, Month month) {}
}
