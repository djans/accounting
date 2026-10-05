package com.cogitosum.service;

import com.cogitosum.dto.TaxReturnRowDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Service
public class TaxReturnReportPdfService {

    private final MessageSource messageSource;

    public TaxReturnReportPdfService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public byte[] create(TaxFilingService.TaxReturnReportData report, Locale locale) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream();
             ReturnPdfLayout layout = new ReturnPdfLayout(document, report, locale, messageSource)) {
            document.getDocumentInformation().setTitle(message("tax.report.title", locale));
            for (TaxReturnRowDTO row : report.rows()) {
                layout.row(row);
            }
            if (report.reconstructed()) {
                layout.note(message("tax.report.reconstructed", locale));
            }
            layout.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate the tax return report PDF", exception);
        }
    }

    private String message(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }

    private static final class ReturnPdfLayout implements AutoCloseable {
        private static final float LEFT = 42;
        private static final float RIGHT = 570;
        private static final float BOTTOM = 48;
        private static final float LINE_HEIGHT = 15;
        private static final float LINE_X = 42;
        private static final float DESCRIPTION_X = 86;
        private static final float AMOUNT_RIGHT = 470;
        private static final float BALANCE_RIGHT = 568;
        private static final float DESCRIPTION_WIDTH = 286;
        private static final float MONEY_WIDTH = 88;

        private final PDDocument document;
        private final TaxFilingService.TaxReturnReportData report;
        private final Locale locale;
        private final MessageSource messageSource;
        private final PDType1Font regular = new PDType1Font(FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(FontName.HELVETICA_BOLD);
        private final NumberFormat currencyFormat;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        private ReturnPdfLayout(PDDocument document, TaxFilingService.TaxReturnReportData report,
                                Locale locale, MessageSource messageSource) throws IOException {
            this.document = document;
            this.report = report;
            this.locale = locale;
            this.messageSource = messageSource;
            currencyFormat = NumberFormat.getCurrencyInstance(locale);
            currencyFormat.setCurrency(Currency.getInstance("CAD"));
            currencyFormat.setMinimumFractionDigits(2);
            currencyFormat.setMaximumFractionDigits(2);
            newPage();
        }

        private void row(TaxReturnRowDTO row) throws IOException {
            PDType1Font font = row.total() ? bold : regular;
            float size = 9;
            List<String> descriptionLines = wrap(message(row.descriptionKey()), font, size, DESCRIPTION_WIDTH);
            float rowHeight = Math.max(LINE_HEIGHT, descriptionLines.size() * LINE_HEIGHT);
            if (y - rowHeight < BOTTOM + LINE_HEIGHT) {
                newPage();
            }
            for (int i = 0; i < descriptionLines.size(); i++) {
                text(descriptionLines.get(i), DESCRIPTION_X, y - i * LINE_HEIGHT, font, size);
            }
            text(row.line() == null ? "" : row.line(), LINE_X, y, font, size);
            if (row.amount() != null) {
                rightText(money(row.amount()), AMOUNT_RIGHT, y, font, size, MONEY_WIDTH);
            }
            if (row.balance() != null) {
                rightText(money(row.balance()), BALANCE_RIGHT, y, font, size, MONEY_WIDTH);
            }
            y -= rowHeight;
        }

        private void note(String value) throws IOException {
            List<String> lines = wrap(value, regular, 8, RIGHT - LEFT);
            float height = lines.size() * LINE_HEIGHT;
            if (y - height < BOTTOM + LINE_HEIGHT) {
                newPage();
            }
            for (String line : lines) {
                text(line, LEFT, y, regular, 8);
                y -= LINE_HEIGHT;
            }
        }

        private void newPage() throws IOException {
            if (stream != null) {
                footer();
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            pageNumber++;
            y = 750;
            List<String> companyLines = wrap(report.companyName(), bold, 11, RIGHT - LEFT);
            for (String line : companyLines) {
                text(line, LEFT, y, bold, 11);
                y -= 13;
            }
            y -= 8;
            text(message("tax.report.title"), LEFT, y, bold, 16);
            y -= 20;
            List<String> agencyLines = wrap(report.agencyName(), regular, 10, 365);
            for (int index = 0; index < agencyLines.size(); index++) {
                text(agencyLines.get(index), LEFT, y - index * 12, regular, 10);
            }
            text(message("tax.report.basis"), RIGHT - 110, y, regular, 9);
            y -= Math.max(12, agencyLines.size() * 12) + 4;
            List<String> periodLines = wrap(periodLabel(), regular, 9, RIGHT - LEFT);
            for (int index = 0; index < periodLines.size(); index++) {
                text(periodLines.get(index), LEFT, y - index * 11, regular, 9);
            }
            y -= periodLines.size() * 11 + 4;
            rule();
            tableHeader();
        }

        private String periodLabel() {
            DateTimeFormatter monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale);
            DateTimeFormatter monthOnly = DateTimeFormatter.ofPattern("MMMM", locale);
            LocalDate start = report.periodStart();
            LocalDate end = report.periodEnd();
            if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear()) {
                return message("tax.report.period.single", start.format(monthYear));
            }
            if (start.getYear() == end.getYear()) {
                String startMonth = start.format(monthOnly);
                String endMonthYear = end.format(monthYear);
                return message("tax.report.period", startMonth, endMonthYear);
            }
            return message("tax.report.period", start.format(monthYear), end.format(monthYear));
        }

        private void tableHeader() throws IOException {
            text(message("tax.report.line"), LINE_X, y, bold, 8);
            text(message("tax.report.description"), DESCRIPTION_X, y, bold, 8);
            rightText(message("tax.report.amount"), AMOUNT_RIGHT, y, bold, 8, MONEY_WIDTH);
            rightText(message("tax.report.balance"), BALANCE_RIGHT, y, bold, 8, MONEY_WIDTH);
            y -= 8;
            rule();
        }

        private void rule() throws IOException {
            stream.moveTo(LEFT, y + 4);
            stream.lineTo(RIGHT, y + 4);
            stream.stroke();
            y -= 6;
        }

        private void footer() throws IOException {
            stream.moveTo(LEFT, 36);
            stream.lineTo(RIGHT, 36);
            stream.stroke();
            rightText(message("tax.report.page", pageNumber), RIGHT, 22, regular, 8, 80);
        }

        private List<String> wrap(String value, PDType1Font font, float size, float maxWidth)
                throws IOException {
            String safe = InvoicePdfService.safeText(value);
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : safe.split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && width(candidate, font, size) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(candidate);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        private float width(String value, PDType1Font font, float size) throws IOException {
            return font.getStringWidth(value) / 1000 * size;
        }

        private void text(String value, float x, float baseline, PDType1Font font, float size)
                throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(InvoicePdfService.safeText(value));
            stream.endText();
        }

        private void rightText(String value, float rightEdge, float baseline, PDType1Font font,
                               float size, float maxWidth) throws IOException {
            String safe = InvoicePdfService.safeText(value);
            float textWidth = Math.min(width(safe, font, size), maxWidth);
            text(safe, rightEdge - textWidth, baseline, font, size);
        }

        private String money(BigDecimal amount) {
            return currencyFormat.format(amount).replace('\u00a0', ' ').replace('\u202f', ' ');
        }

        private String message(String key, Object... args) {
            return messageSource.getMessage(key, args, locale);
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                footer();
                stream.close();
                stream = null;
            }
        }
    }
}
