package com.cogitosum.service;

import com.cogitosum.entity.Customer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class CustomerStatementPdfService {

    private static final float LEFT = 48;
    private static final float TOP = 740;
    private static final float BOTTOM = 52;
    private static final float LINE_HEIGHT = 15;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public byte[] createPdf(CustomerStatementService.Statement statement) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Layout layout = new Layout(document);
            Customer customer = statement.customer();
            layout.line("CUSTOMER ACCOUNT STATEMENT", true);
            if (customer.getCompany() != null) {
                layout.line(safe(customer.getCompany().getLegalName()), false);
                layout.line(safe(customer.getCompany().getEmail()), false);
            }
            layout.space();
            layout.line("Customer: " + safe(customer.getBusinessName()), true);
            layout.line("Contact: " + safe(customer.getName()) + "  " + safe(customer.getEmail()), false);
            layout.line("Statement period: " + (statement.startDate() == null
                    ? "All activity" : DATE_FORMAT.format(statement.startDate()))
                    + " to " + DATE_FORMAT.format(statement.endDate()), false);
            layout.space();
            layout.line(String.format(Locale.ROOT, "%-10s %-34s %10s %10s %10s",
                    "Date", "Description", "Debit", "Credit", "Balance"), true);
            layout.rule();
            BigDecimal balance = statement.openingBalance();
            layout.line(String.format(Locale.ROOT, "%-10s %-34s %10s %10s %10s",
                    "", "Opening balance", "", "", currency(balance)), false);
            for (CustomerStatementService.StatementLine line : statement.lines()) {
                if (layout.needsNewPage()) {
                    layout.newPage();
                    layout.line(String.format(Locale.ROOT, "%-10s %-34s %10s %10s %10s",
                            "Date", "Description", "Debit", "Credit", "Balance"), true);
                    layout.rule();
                }
                layout.line(String.format(Locale.ROOT, "%-10s %-34s %10s %10s %10s",
                        DATE_FORMAT.format(line.date()),
                        truncate(safe(line.description()), 34),
                        currency(line.debit()), currency(line.credit()), currency(line.balance())), false);
            }
            layout.rule();
            layout.line("Closing balance: " + currency(statement.closingBalance()), true);
            layout.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate the customer statement PDF", exception);
        }
    }

    public static String filenameFor(Customer customer, java.time.LocalDate startDate,
                                     java.time.LocalDate endDate) {
        String name = customer == null ? "customer" : customer.getBusinessName();
        String safeName = safe(name).replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("[-.]{2,}", "-").replaceAll("^[.-]+|[.-]+$", "");
        if (safeName.isBlank()) {
            safeName = "customer";
        }
        String from = startDate == null ? "all" : DATE_FORMAT.format(startDate);
        return "statement-" + safeName.substring(0, Math.min(safeName.length(), 70))
                + "-" + from + "-to-" + DATE_FORMAT.format(endDate) + ".pdf";
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[\\p{Cntrl}]", " ");
        StringBuilder safe = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            safe.append(character >= 32 && character <= 126 ? character : ' ');
        }
        return safe.toString().replaceAll("\\s+", " ").trim();
    }

    private static String currency(BigDecimal amount) {
        return amount == null ? "$0.00" : String.format(Locale.ROOT, "$%,.2f", amount);
    }

    private static String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length - 3) + "...";
    }

    private static final class Layout {
        private final PDDocument document;
        private final PDType1Font regular = new PDType1Font(FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;

        private Layout(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void line(String value, boolean emphasized) throws IOException {
            if (needsNewPage()) {
                newPage();
            }
            stream.beginText();
            stream.setFont(emphasized ? bold : regular, emphasized ? 10 : 8);
            stream.newLineAtOffset(LEFT, y);
            stream.showText(safe(value));
            stream.endText();
            y -= LINE_HEIGHT;
        }

        private void space() {
            y -= 8;
        }

        private void rule() throws IOException {
            stream.moveTo(LEFT, y + 5);
            stream.lineTo(PDRectangle.LETTER.getWidth() - LEFT, y + 5);
            stream.stroke();
            y -= 7;
        }

        private boolean needsNewPage() {
            return y < BOTTOM;
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            document.addPage(new PDPage(PDRectangle.LETTER));
            stream = new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1));
            y = TOP;
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
