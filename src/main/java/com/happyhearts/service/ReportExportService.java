package com.happyhearts.service;

import com.happyhearts.enums.AttendanceStatus;
import com.happyhearts.enums.ExportFormat;
import com.happyhearts.model.Branch;
import com.happyhearts.model.DailyAttendanceSummary;
import com.happyhearts.model.Employee;
import com.happyhearts.util.HappyHeartsPdfTemplate;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ReportExportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public byte[] export(
            Branch branch,
            List<DailyAttendanceSummary> summaries,
            LocalDate startDate,
            LocalDate endDate,
            String generatedBy,
            ExportFormat format
    ) {
        try {
            return switch (format) {
                case EXCEL -> exportExcel(branch, summaries, startDate, endDate, generatedBy);
                case PDF -> exportPdf(branch, summaries, startDate, endDate, generatedBy);
            };
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] exportExcel(
            Branch branch,
            List<DailyAttendanceSummary> summaries,
            LocalDate startDate,
            LocalDate endDate,
            String generatedBy
    ) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Attendance");

            XSSFCellStyle navy = excelStyle(wb, HappyHeartsPdfTemplate.NAVY, Color.WHITE, true, HorizontalAlignment.CENTER);
            XSSFCellStyle accent = excelStyle(wb, HappyHeartsPdfTemplate.ORANGE, Color.WHITE, true, HorizontalAlignment.CENTER);
            XSSFCellStyle detailLabel = excelStyle(wb, HappyHeartsPdfTemplate.LIGHT_GRAY, HappyHeartsPdfTemplate.MUTED, true, HorizontalAlignment.LEFT);
            XSSFCellStyle detailValue = excelStyle(wb, HappyHeartsPdfTemplate.LIGHT_GRAY, HappyHeartsPdfTemplate.NAVY, true, HorizontalAlignment.LEFT);
            XSSFCellStyle header = excelStyle(wb, HappyHeartsPdfTemplate.NAVY, Color.WHITE, true, HorizontalAlignment.LEFT);
            XSSFCellStyle rowWhite = excelStyle(wb, Color.WHITE, HappyHeartsPdfTemplate.TEXT, false, HorizontalAlignment.LEFT);
            XSSFCellStyle rowTint = excelStyle(wb, HappyHeartsPdfTemplate.LIGHT_ORANGE, HappyHeartsPdfTemplate.TEXT, false, HorizontalAlignment.LEFT);
            XSSFCellStyle numWhite = excelStyle(wb, Color.WHITE, HappyHeartsPdfTemplate.TEXT, false, HorizontalAlignment.RIGHT);
            XSSFCellStyle numTint = excelStyle(wb, HappyHeartsPdfTemplate.LIGHT_ORANGE, HappyHeartsPdfTemplate.TEXT, false, HorizontalAlignment.RIGHT);

            int r = 0;
            Row title = sheet.createRow(r++);
            title.setHeightInPoints(28);
            createCell(title, 0, "Happy Hearts - Attendance Management System", navy);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

            Row orangeBar = sheet.createRow(r++);
            orangeBar.setHeightInPoints(6);
            createCell(orangeBar, 0, "", accent);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));

            Row reportTitle = sheet.createRow(r++);
            reportTitle.setHeightInPoints(24);
            createCell(reportTitle, 0, "ATTENDANCE REPORT", navy);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 6));
            r++;

            Row detail1 = sheet.createRow(r++);
            createCell(detail1, 0, "BRANCH", detailLabel);
            createCell(detail1, 1, branchLabel(branch), detailValue);
            createCell(detail1, 3, "PERIOD", detailLabel);
            createCell(detail1, 4, periodLabel(startDate, endDate), detailValue);

            Row detail2 = sheet.createRow(r++);
            createCell(detail2, 0, "GENERATED BY", detailLabel);
            createCell(detail2, 1, safe(generatedBy), detailValue);
            createCell(detail2, 3, "GENERATED ON", detailLabel);
            createCell(detail2, 4, DATE_TIME.format(LocalDateTime.now()), detailValue);
            r++;

            SummaryStats stats = SummaryStats.from(summaries);
            Row statsRow = sheet.createRow(r++);
            statsRow.setHeightInPoints(32);
            createCell(statsRow, 0, "TOTAL: " + stats.total(), excelStyle(wb, HappyHeartsPdfTemplate.ORANGE, Color.WHITE, true, HorizontalAlignment.CENTER));
            createCell(statsRow, 1, "PRESENT: " + stats.present(), excelStyle(wb, HappyHeartsPdfTemplate.GREEN, Color.WHITE, true, HorizontalAlignment.CENTER));
            createCell(statsRow, 2, "ABSENT: " + stats.absent(), excelStyle(wb, HappyHeartsPdfTemplate.RED, Color.WHITE, true, HorizontalAlignment.CENTER));
            createCell(statsRow, 3, "LATE: " + stats.late(), excelStyle(wb, HappyHeartsPdfTemplate.AMBER, Color.WHITE, true, HorizontalAlignment.CENTER));
            r++;

            Row h = sheet.createRow(r++);
            String[] headers = {"#", "Date", "Employee", "Role", "Status", "Entry", "Exit", "Hours"};
            for (int i = 0; i < headers.length; i++) {
                createCell(h, i, headers[i], header);
            }

            int index = 1;
            for (DailyAttendanceSummary s : summaries) {
                boolean alternate = index % 2 == 0;
                Row row = sheet.createRow(r++);
                createCell(row, 0, String.valueOf(index), alternate ? numTint : numWhite);
                createCell(row, 1, safe(s.getSummaryDate()), alternate ? rowTint : rowWhite);
                createCell(row, 2, employeeName(s.getEmployee()), alternate ? rowTint : rowWhite);
                createCell(row, 3, employeeRole(s.getEmployee()), alternate ? rowTint : rowWhite);
                createCell(row, 4, statusLabel(s.getStatus()), excelStatusStyle(wb, s.getStatus()));
                createCell(row, 5, safe(s.getEntryTime()), alternate ? rowTint : rowWhite);
                createCell(row, 6, safe(s.getExitTime()), alternate ? rowTint : rowWhite);
                createCell(row, 7, hours(s), alternate ? numTint : numWhite);
                index++;
            }

            r++;
            Row footer = sheet.createRow(r);
            createCell(footer, 0, "Confidential - Happy Hearts © " + LocalDate.now().getYear(), detailLabel);
            sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 6));

            for (int c = 0; c <= 7; c++) {
                sheet.autoSizeColumn(c);
            }
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private byte[] exportPdf(
            Branch branch,
            List<DailyAttendanceSummary> summaries,
            LocalDate startDate,
            LocalDate endDate,
            String generatedBy
    ) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Document document = HappyHeartsPdfTemplate.createPortraitDocument();
            HappyHeartsPdfTemplate.attach(document, bos);
            document.open();

            SummaryStats stats = SummaryStats.from(summaries);
            HappyHeartsPdfTemplate.addReportTitle(document, "Attendance Report");
            HappyHeartsPdfTemplate.addDetailsCard(document, List.of(
                    new HappyHeartsPdfTemplate.DetailItem("Branch", branchLabel(branch)),
                    new HappyHeartsPdfTemplate.DetailItem("Period", periodLabel(startDate, endDate)),
                    new HappyHeartsPdfTemplate.DetailItem("Generated by", safe(generatedBy)),
                    new HappyHeartsPdfTemplate.DetailItem("Generated on", DATE_TIME.format(LocalDateTime.now()))
            ));
            document.add(HappyHeartsPdfTemplate.statsRow(List.of(
                    new HappyHeartsPdfTemplate.StatBox("Total Employees", String.valueOf(stats.total()), HappyHeartsPdfTemplate.ORANGE),
                    new HappyHeartsPdfTemplate.StatBox("Present", String.valueOf(stats.present()), HappyHeartsPdfTemplate.GREEN),
                    new HappyHeartsPdfTemplate.StatBox("Absent", String.valueOf(stats.absent()), HappyHeartsPdfTemplate.RED),
                    new HappyHeartsPdfTemplate.StatBox("Late", String.valueOf(stats.late()), HappyHeartsPdfTemplate.AMBER),
                    new HappyHeartsPdfTemplate.StatBox("Leave", String.valueOf(stats.leave()), HappyHeartsPdfTemplate.PURPLE)
            )));

            addAttendanceTable(document, summaries);
            addBranchStatsTable(document, branch, stats);
            HappyHeartsPdfTemplate.addSignatureSection(document);
            document.close();
            return bos.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void addExecutiveSummary(Document document, SummaryStats stats) throws DocumentException {
        HappyHeartsPdfTemplate.addSectionTitle(document, "Executive Summary");
        PdfPTable summary = new PdfPTable(new float[]{1.2f, 1, 1, 1});
        summary.setWidthPercentage(100);
        summary.setSpacingAfter(12);
        summary.addCell(summaryCell("Attendance Rate", stats.rate() + "%", rateColor(stats.rate())));
        summary.addCell(summaryCell("Present", String.valueOf(stats.present()), HappyHeartsPdfTemplate.GREEN));
        summary.addCell(summaryCell("Absent", String.valueOf(stats.absent()), HappyHeartsPdfTemplate.RED));
        summary.addCell(summaryCell("Late", String.valueOf(stats.late()), HappyHeartsPdfTemplate.AMBER));
        document.add(summary);
    }

    private void addHighlights(Document document, List<DailyAttendanceSummary> summaries) throws DocumentException {
        Map<String, EmployeeRate> rates = employeeRates(summaries);
        if (rates.isEmpty()) {
            return;
        }
        EmployeeRate best = rates.values().stream().max(Comparator.comparing(EmployeeRate::rate)).orElse(null);
        EmployeeRate worst = rates.values().stream().min(Comparator.comparing(EmployeeRate::rate)).orElse(null);
        if (best == null || worst == null) {
            return;
        }

        PdfPTable highlights = new PdfPTable(2);
        highlights.setWidthPercentage(100);
        highlights.setSpacingAfter(14);
        highlights.addCell(highlightCell("Best Attendance", best.name() + " - " + best.rate() + "%", HappyHeartsPdfTemplate.GREEN));
        highlights.addCell(highlightCell("Needs Attention", worst.name() + " - " + worst.rate() + "%", rateColor(worst.rate())));
        document.add(highlights);
    }

    private void addAttendanceTable(Document document, List<DailyAttendanceSummary> summaries) throws DocumentException {
        HappyHeartsPdfTemplate.addSectionTitle(document, "Attendance Details");
        PdfPTable table = new PdfPTable(new float[]{0.45f, 1.05f, 2.1f, 1.55f, 1.05f, 0.85f, 0.85f, 0.75f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : List.of("#", "Date", "Full Name", "Role", "Status", "Entry", "Exit", "Hours")) {
            table.addCell(HappyHeartsPdfTemplate.headerCell(header));
        }

        int index = 1;
        for (DailyAttendanceSummary s : summaries) {
            boolean alternate = index % 2 == 0;
            table.addCell(HappyHeartsPdfTemplate.numberCell(String.valueOf(index), alternate));
            table.addCell(HappyHeartsPdfTemplate.dataCell(safe(s.getSummaryDate()), alternate));
            table.addCell(HappyHeartsPdfTemplate.dataCell(employeeName(s.getEmployee()), alternate));
            table.addCell(HappyHeartsPdfTemplate.dataCell(employeeRole(s.getEmployee()), alternate));
            table.addCell(HappyHeartsPdfTemplate.statusCell(statusLabel(s.getStatus()), alternate));
            table.addCell(HappyHeartsPdfTemplate.dataCell(safe(s.getEntryTime()), alternate));
            table.addCell(HappyHeartsPdfTemplate.dataCell(safe(s.getExitTime()), alternate));
            table.addCell(HappyHeartsPdfTemplate.numberCell(hours(s), alternate));
            index++;
        }

        if (summaries.isEmpty()) {
            PdfPCell empty = HappyHeartsPdfTemplate.dataCell("No attendance rows found for this period.", false);
            empty.setColspan(8);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(empty);
        } else {
            table.addCell(HappyHeartsPdfTemplate.totalCell("Total records", 7));
            table.addCell(HappyHeartsPdfTemplate.totalCell(String.valueOf(summaries.size()), 1));
        }
        table.setSpacingAfter(14);
        document.add(table);
    }

    private void addBranchStatsTable(Document document, Branch branch, SummaryStats stats) throws DocumentException {
        HappyHeartsPdfTemplate.addSectionTitle(document, "Branch Statistics");
        PdfPTable table = new PdfPTable(new float[]{2.3f, 1, 1, 1, 1, 1});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : List.of("Branch", "Headcount", "Present", "Absent", "Late", "Rate")) {
            table.addCell(HappyHeartsPdfTemplate.headerCell(header));
        }
        table.addCell(HappyHeartsPdfTemplate.dataCell(branchLabel(branch), false));
        table.addCell(HappyHeartsPdfTemplate.numberCell(String.valueOf(stats.headcount()), false));
        table.addCell(HappyHeartsPdfTemplate.numberCell(String.valueOf(stats.present()), false));
        table.addCell(HappyHeartsPdfTemplate.numberCell(String.valueOf(stats.absent()), false));
        table.addCell(HappyHeartsPdfTemplate.numberCell(String.valueOf(stats.late()), false));
        PdfPCell rate = HappyHeartsPdfTemplate.numberCell(stats.rate() + "%", false);
        rate.setPhrase(new Phrase(stats.rate() + "%", HappyHeartsPdfTemplate.font(9, Font.BOLD, rateColor(stats.rate()))));
        table.addCell(rate);
        table.setSpacingAfter(8);
        document.add(table);
    }

    private PdfPCell summaryCell(String label, String value, Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(10);
        cell.setBackgroundColor(HappyHeartsPdfTemplate.LIGHT_GRAY);
        var valueText = new com.lowagie.text.Paragraph(value, HappyHeartsPdfTemplate.font(20, Font.BOLD, color));
        valueText.setAlignment(Element.ALIGN_CENTER);
        valueText.setSpacingAfter(3);
        cell.addElement(valueText);
        var labelText = new com.lowagie.text.Paragraph(label.toUpperCase(), HappyHeartsPdfTemplate.font(8, Font.BOLD, HappyHeartsPdfTemplate.MUTED));
        labelText.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(labelText);
        return cell;
    }

    private PdfPCell highlightCell(String label, String value, Color color) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(HappyHeartsPdfTemplate.BORDER);
        cell.setPadding(9);
        cell.setBackgroundColor(HappyHeartsPdfTemplate.LIGHT_ORANGE);
        cell.addElement(new com.lowagie.text.Paragraph(label.toUpperCase(), HappyHeartsPdfTemplate.font(8, Font.BOLD, HappyHeartsPdfTemplate.MUTED)));
        cell.addElement(new com.lowagie.text.Paragraph(value, HappyHeartsPdfTemplate.font(11, Font.BOLD, color)));
        return cell;
    }

    private static Map<String, EmployeeRate> employeeRates(List<DailyAttendanceSummary> summaries) {
        Map<String, List<DailyAttendanceSummary>> byEmployee = summaries.stream()
                .filter(s -> s.getEmployee() != null)
                .collect(Collectors.groupingBy(
                        s -> String.valueOf(s.getEmployee().getId()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<String, EmployeeRate> rates = new LinkedHashMap<>();
        for (List<DailyAttendanceSummary> rows : byEmployee.values()) {
            if (rows.isEmpty()) {
                continue;
            }
            long attended = rows.stream().filter(s -> s.getStatus() != AttendanceStatus.ABSENT).count();
            int rate = percent(attended, rows.size());
            rates.put(String.valueOf(rows.get(0).getEmployee().getId()), new EmployeeRate(employeeName(rows.get(0).getEmployee()), rate));
        }
        return rates;
    }

    private static String branchLabel(Branch branch) {
        if (branch == null) {
            return "-";
        }
        String code = safe(branch.getCode());
        String name = safe(branch.getName());
        String location = safe(branch.getLocation());
        String label = code + " - " + name;
        return "-".equals(location) ? label : label + " (" + location + ")";
    }

    private static String periodLabel(LocalDate startDate, LocalDate endDate) {
        return HappyHeartsPdfTemplate.formatDate(startDate) + " - " + HappyHeartsPdfTemplate.formatDate(endDate);
    }

    private static String employeeName(Employee employee) {
        if (employee == null) {
            return "-";
        }
        return (safe(employee.getFirstName()) + " " + safe(employee.getLastName())).trim();
    }

    private static String employeeRole(Employee employee) {
        if (employee == null || employee.getCategory() == null) {
            return "-";
        }
        return employee.getCategory().name().replace('_', ' ');
    }

    private static String statusLabel(AttendanceStatus status) {
        return status == null ? "-" : status.name().replace('_', ' ');
    }

    private static String hours(DailyAttendanceSummary summary) {
        BigDecimal hours = summary == null ? null : summary.getTotalHours();
        return hours == null ? "-" : hours.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Color rateColor(int rate) {
        if (rate >= 80) {
            return HappyHeartsPdfTemplate.GREEN;
        }
        if (rate >= 60) {
            return new Color(0xE6, 0x51, 0x00);
        }
        return HappyHeartsPdfTemplate.RED;
    }

    private static int percent(long count, long total) {
        if (total <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private static String safe(Object value) {
        return HappyHeartsPdfTemplate.safe(value);
    }

    private static void createCell(Row row, int column, String value, XSSFCellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static XSSFCellStyle excelStyle(XSSFWorkbook wb, Color bg, Color fg, boolean bold, HorizontalAlignment alignment) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(xlsxColor(bg));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(alignment);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        XSSFFont font = wb.createFont();
        font.setColor(xlsxColor(fg));
        font.setBold(bold);
        style.setFont(font);
        return style;
    }

    private static XSSFCellStyle excelStatusStyle(XSSFWorkbook wb, AttendanceStatus status) {
        Color bg = switch (status == null ? "" : status.name()) {
            case "PRESENT" -> new Color(0xE8, 0xF5, 0xE9);
            case "ABSENT" -> new Color(0xFF, 0xEB, 0xEE);
            case "LATE" -> new Color(0xFF, 0xF3, 0xE0);
            case "HALF_DAY" -> new Color(0xF5, 0xF3, 0xFF);
            default -> Color.WHITE;
        };
        Color fg = switch (status == null ? "" : status.name()) {
            case "PRESENT" -> HappyHeartsPdfTemplate.GREEN;
            case "ABSENT" -> HappyHeartsPdfTemplate.RED;
            case "LATE" -> new Color(0xE6, 0x51, 0x00);
            case "HALF_DAY" -> HappyHeartsPdfTemplate.PURPLE;
            default -> HappyHeartsPdfTemplate.TEXT;
        };
        return excelStyle(wb, bg, fg, true, HorizontalAlignment.CENTER);
    }

    private static XSSFColor xlsxColor(Color color) {
        return new XSSFColor(new byte[]{
                (byte) color.getRed(),
                (byte) color.getGreen(),
                (byte) color.getBlue()
        }, new DefaultIndexedColorMap());
    }

    private record EmployeeRate(String name, int rate) {
    }

    private record SummaryStats(int total, long present, long absent, long late, long leave, int headcount, int rate) {
        static SummaryStats from(List<DailyAttendanceSummary> summaries) {
            List<DailyAttendanceSummary> safeRows = summaries == null ? new ArrayList<>() : summaries;
            long present = safeRows.stream().filter(s -> s.getStatus() == AttendanceStatus.PRESENT).count();
            long absent = safeRows.stream().filter(s -> s.getStatus() == AttendanceStatus.ABSENT).count();
            long late = safeRows.stream().filter(s -> s.getStatus() == AttendanceStatus.LATE).count();
            long leave = safeRows.stream().filter(s -> s.getStatus() == AttendanceStatus.HALF_DAY).count();
            long attended = safeRows.stream().filter(s -> s.getStatus() != AttendanceStatus.ABSENT).count();
            int headcount = (int) safeRows.stream()
                    .map(DailyAttendanceSummary::getEmployee)
                    .filter(Objects::nonNull)
                    .map(Employee::getId)
                    .distinct()
                    .count();
            return new SummaryStats(safeRows.size(), present, absent, late, leave, headcount, percent(attended, safeRows.size()));
        }
    }
}
