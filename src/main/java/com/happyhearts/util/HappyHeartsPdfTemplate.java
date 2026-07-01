package com.happyhearts.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class HappyHeartsPdfTemplate {

    public static final Color NAVY = new Color(0xF9, 0x73, 0x16);
    public static final Color ORANGE = new Color(0xF9, 0x73, 0x16);
    public static final Color ORANGE_DARK = new Color(0xEA, 0x58, 0x0C);
    public static final Color ORANGE_100 = new Color(0xFF, 0xED, 0xD5);
    public static final Color GREEN = new Color(0x16, 0xA3, 0x4A);
    public static final Color RED = new Color(0xDC, 0x26, 0x26);
    public static final Color AMBER = new Color(0xF5, 0x9E, 0x0B);
    public static final Color PURPLE = new Color(0x8B, 0x5C, 0xF6);
    public static final Color LIGHT_ORANGE = new Color(0xFF, 0xF7, 0xED);
    public static final Color LIGHT_GRAY = new Color(0xFF, 0xF7, 0xED);
    public static final Color BORDER = new Color(0xE5, 0xE7, 0xEB);
    public static final Color TEXT = new Color(0x1F, 0x29, 0x37);
    public static final Color MUTED = new Color(0x6B, 0x72, 0x80);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private HappyHeartsPdfTemplate() {
    }

    public static Document createPortraitDocument() {
        return new Document(PageSize.A4, 40, 40, 92, 54);
    }

    public static PdfWriter attach(Document document, OutputStream outputStream) throws DocumentException {
        PdfWriter writer = PdfWriter.getInstance(document, outputStream);
        writer.setPageEvent(new HeaderFooter());
        return writer;
    }

    public static Font font(float size, int style, Color color) {
        return new Font(Font.HELVETICA, size, style, color);
    }

    public static void addReportTitle(Document document, String title) throws DocumentException {
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(2);
        document.add(spacer);

        Paragraph heading = new Paragraph(safe(title).toUpperCase(), font(12, Font.BOLD, ORANGE_DARK));
        heading.setAlignment(Element.ALIGN_LEFT);
        heading.setSpacingAfter(8);
        document.add(heading);
    }

    public static void addDetailsCard(Document document, List<DetailItem> details) throws DocumentException {
        PdfPTable card = new PdfPTable(new float[]{0.02f, 0.49f, 0.49f});
        card.setWidthPercentage(100);
        card.setSpacingAfter(16);

        PdfPCell accent = new PdfPCell(new Phrase(""));
        accent.setBorder(Rectangle.NO_BORDER);
        accent.setBackgroundColor(ORANGE);
        accent.setMinimumHeight(54);
        card.addCell(accent);

        PdfPCell left = detailsCell(details, 0);
        PdfPCell right = detailsCell(details, 1);
        card.addCell(left);
        card.addCell(right);
        document.add(card);
    }

    public static PdfPTable statsRow(List<StatBox> stats) throws DocumentException {
        PdfPTable table = new PdfPTable(stats.size());
        table.setWidthPercentage(100);
        table.setSpacingAfter(18);
        table.getDefaultCell().setBorder(Rectangle.NO_BORDER);

        for (StatBox stat : stats) {
            PdfPCell cell = new PdfPCell();
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPadding(11);
            cell.setBackgroundColor(stat.color());

            Paragraph value = new Paragraph(safe(stat.value()), font(18, Font.BOLD, Color.WHITE));
            value.setAlignment(Element.ALIGN_CENTER);
            value.setSpacingAfter(2);
            cell.addElement(value);

            Paragraph label = new Paragraph(safe(stat.label()).toUpperCase(), font(7, Font.BOLD, Color.WHITE));
            label.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(label);
            table.addCell(cell);
        }
        return table;
    }

    public static void addSectionTitle(Document document, String title) throws DocumentException {
        PdfPTable section = new PdfPTable(new float[]{0.012f, 0.988f});
        section.setWidthPercentage(100);
        section.setSpacingBefore(4);
        section.setSpacingAfter(7);

        PdfPCell bar = new PdfPCell(new Phrase(""));
        bar.setBorder(Rectangle.NO_BORDER);
        bar.setBackgroundColor(ORANGE);
        bar.setFixedHeight(15);
        section.addCell(bar);

        PdfPCell label = new PdfPCell(new Phrase("  " + safe(title).toUpperCase(), font(10, Font.BOLD, ORANGE_DARK)));
        label.setBorder(Rectangle.NO_BORDER);
        label.setPaddingBottom(4);
        section.addCell(label);
        document.add(section);
    }

    public static PdfPCell headerCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text).toUpperCase(), font(9, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(ORANGE);
        cell.setBorderColor(ORANGE);
        cell.setPadding(7);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    public static PdfPCell dataCell(String text, boolean alternate) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font(9, Font.NORMAL, TEXT)));
        cell.setBackgroundColor(alternate ? LIGHT_ORANGE : Color.WHITE);
        cell.setBorderColor(BORDER);
        cell.setPadding(6);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    public static PdfPCell numberCell(String text, boolean alternate) {
        PdfPCell cell = dataCell(text, alternate);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    public static PdfPCell statusCell(String status, boolean alternate) {
        String normalized = safe(status).toUpperCase();
        Color bg = switch (normalized) {
            case "PRESENT", "ON TIME" -> new Color(0xE8, 0xF5, 0xE9);
            case "ABSENT" -> new Color(0xFF, 0xEB, 0xEE);
            case "LATE" -> new Color(0xFF, 0xF3, 0xE0);
            case "LEAVE", "HALF_DAY" -> new Color(0xF5, 0xF3, 0xFF);
            default -> alternate ? LIGHT_ORANGE : Color.WHITE;
        };
        Color fg = switch (normalized) {
            case "PRESENT", "ON TIME" -> GREEN;
            case "ABSENT" -> RED;
            case "LATE" -> new Color(0xE6, 0x51, 0x00);
            case "LEAVE", "HALF_DAY" -> PURPLE;
            default -> TEXT;
        };
        PdfPCell cell = new PdfPCell(new Phrase(normalized, font(8, Font.BOLD, fg)));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(BORDER);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    public static PdfPCell totalCell(String text, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font(9, Font.BOLD, Color.WHITE)));
        cell.setColspan(colspan);
        cell.setBackgroundColor(ORANGE);
        cell.setBorderColor(ORANGE);
        cell.setPadding(7);
        return cell;
    }

    public static void addSignatureSection(Document document) throws DocumentException {
        PdfPTable signatures = new PdfPTable(2);
        signatures.setWidthPercentage(100);
        signatures.setSpacingBefore(18);
        signatures.setWidths(new float[]{1, 1});
        signatures.addCell(signatureCell("Prepared by"));
        signatures.addCell(signatureCell("Approved by"));
        document.add(signatures);
    }

    public static String safe(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "-" : text;
    }

    public static String formatDate(LocalDate date) {
        return date == null ? "-" : DATE_FORMAT.format(date);
    }

    private static PdfPCell detailsCell(List<DetailItem> details, int parity) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(12);

        for (int i = parity; i < details.size(); i += 2) {
            DetailItem item = details.get(i);
            Paragraph label = new Paragraph(safe(item.label()).toUpperCase(), font(7, Font.BOLD, MUTED));
            label.setSpacingAfter(1);
            cell.addElement(label);

            Paragraph value = new Paragraph(safe(item.value()), font(10, Font.BOLD, ORANGE_DARK));
            value.setSpacingAfter(7);
            cell.addElement(value);
        }
        return cell;
    }

    private static PdfPCell signatureCell(String label) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(10);
        cell.setPaddingRight(18);
        cell.setPaddingBottom(4);

        Paragraph line = new Paragraph("______________________________", font(9, Font.NORMAL, MUTED));
        line.setSpacingAfter(5);
        cell.addElement(line);
        cell.addElement(new Paragraph(label, font(9, Font.BOLD, ORANGE_DARK)));
        cell.addElement(new Paragraph("Name & signature", font(8, Font.ITALIC, MUTED)));
        return cell;
    }

    public record DetailItem(String label, String value) {
    }

    public record StatBox(String label, String value, Color color) {
    }

    private static final class HeaderFooter extends PdfPageEventHelper {
        private PdfTemplate totalPages;
        private BaseFont baseFont;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPages = writer.getDirectContent().createTemplate(30, 12);
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            PdfContentByte canvas = writer.getDirectContent();

            float left = document.leftMargin();
            float right = page.getRight(document.rightMargin());
            float width = right - left;
            float headerTop = page.getTop(0);
            float headerHeight = 58;

            canvas.saveState();
            canvas.setColorFill(ORANGE);
            canvas.rectangle(0, headerTop - headerHeight, page.getWidth(), headerHeight);
            canvas.fill();

            canvas.setColorFill(Color.WHITE);
            canvas.circle(left + 22, headerTop - 29, 14);
            canvas.fill();
            canvas.setColorFill(ORANGE);
            canvas.circle(left + 22, headerTop - 29, 10);
            canvas.fill();

            canvas.beginText();
            canvas.setColorFill(Color.WHITE);
            canvas.setFontAndSize(baseFont, 9);
            canvas.showTextAligned(Element.ALIGN_CENTER, "HH", left + 22, headerTop - 32, 0);
            canvas.setFontAndSize(baseFont, 16);
            canvas.showTextAligned(Element.ALIGN_LEFT, "Happy Hearts", left + 48, headerTop - 24, 0);
            canvas.setColorFill(ORANGE_100);
            canvas.setFontAndSize(baseFont, 9);
            canvas.showTextAligned(Element.ALIGN_LEFT, "Attendance Management System", left + 48, headerTop - 38, 0);
            canvas.setColorFill(Color.WHITE);
            canvas.setFontAndSize(baseFont, 13);
            canvas.showTextAligned(Element.ALIGN_RIGHT, "ATTENDANCE REPORT", right, headerTop - 24, 0);
            canvas.setColorFill(ORANGE_100);
            canvas.setFontAndSize(baseFont, 9);
            canvas.showTextAligned(Element.ALIGN_RIGHT, DATE_FORMAT.format(LocalDate.now()), right, headerTop - 38, 0);
            canvas.endText();

            float footerY = page.getBottom(35);
            canvas.setColorFill(ORANGE);
            canvas.rectangle(0, page.getBottom(0), page.getWidth(), 28);
            canvas.fill();

            canvas.beginText();
            canvas.setColorFill(Color.WHITE);
            canvas.setFontAndSize(baseFont, 9);
            canvas.showTextAligned(Element.ALIGN_LEFT,
                    "Confidential • Happy Hearts © " + Year.now().getValue(),
                    left,
                    page.getBottom(12),
                    0);
            String pageText = "Page " + writer.getPageNumber() + " / ";
            canvas.showTextAligned(Element.ALIGN_RIGHT, pageText, right - 18, page.getBottom(12), 0);
            canvas.endText();
            canvas.addTemplate(totalPages, right - 16, page.getBottom(12));
            canvas.restoreState();
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalPages.beginText();
            totalPages.setFontAndSize(baseFont, 9);
            totalPages.setColorFill(Color.WHITE);
            totalPages.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPages.endText();
        }
    }
}
