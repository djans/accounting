package com.cogitosum.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FinancialStatementsPdfService {

    private final MessageSource messages;

    public FinancialStatementsPdfService(MessageSource messages) {
        this.messages = messages;
    }

    public byte[] generate(Map<String, Object> reports, String companyName, String currency,
                           LocalDate periodStart, LocalDate periodEnd, LocalDate asOf, Locale locale) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Writer writer = new Writer(document, currency);
            writer.line(text(locale, "accounting.pdf.title"), true);
            writer.line(companyName, false);
            writer.line(text(locale, "accounting.pdf.asOf") + ": " + asOf, false);
            writer.line(text(locale, "accounting.pdf.period") + ": " + periodStart + " – " + periodEnd, false);
            writer.space();

            appendTrialBalance(writer, reports.get("trialBalanceBeforeClose"), "accounting.pdf.trialBefore", locale);
            appendTrialBalance(writer, reports.get("trialBalance"), "accounting.pdf.trialAfter", locale);
            appendBalanceSheet(writer, map(reports.get("balanceSheet")), locale);
            appendIncomeStatement(writer, map(reports.get("incomeStatement")), locale);
            writer.finish();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate financial statements PDF", exception);
        }
    }

    private void appendTrialBalance(Writer writer, Object source, String titleKey, Locale locale) throws IOException {
        Map<String, Object> trialBalance = map(source);
        writer.heading(text(locale, titleKey));
        writer.line(text(locale, "accounting.pdf.trialColumns"), true);
        Map<?, ?> accounts = (Map<?, ?>) trialBalance.get("accounts");
        if (accounts != null) {
            for (Object item : accounts.values()) {
                Map<String, Object> row = map(item);
                writer.line(row.get("accountNumber") + "  " + row.get("accountName") + "  "
                        + row.get("accountType") + "    " + writer.amount(row.get("debitBalance")) + " / "
                        + writer.amount(row.get("creditBalance")) + " / " + writer.amount(row.get("balance")), false);
            }
        }
        writer.line(text(locale, "accounting.pdf.totals") + ": "
                + writer.amount(trialBalance.get("totalDebits")) + " / "
                + writer.amount(trialBalance.get("totalCredits")), true);
        writer.space();
    }

    private void appendBalanceSheet(Writer writer, Map<String, Object> balanceSheet, Locale locale) throws IOException {
        writer.heading(text(locale, "accounting.pdf.balanceSheet"));
        appendSection(writer, text(locale, "accounting.pdf.assets"), balanceSheet.get("assets"),
                balanceSheet.get("totalAssets"), locale);
        appendSection(writer, text(locale, "accounting.pdf.liabilities"), balanceSheet.get("liabilities"),
                balanceSheet.get("totalLiabilities"), locale);
        appendSection(writer, text(locale, "accounting.pdf.equity"), balanceSheet.get("equity"),
                balanceSheet.get("totalEquity"), locale);
        writer.line(text(locale, "table.netIncomeCurrentYear") + ": " + writer.amount(balanceSheet.get("netIncome")), false);
        writer.line(text(locale, "table.totalLiabilitiesAndEquity") + ": "
                + writer.amount(balanceSheet.get("totalLiabilitiesAndEquity")), true);
        writer.space();
    }

    private void appendIncomeStatement(Writer writer, Map<String, Object> incomeStatement, Locale locale)
            throws IOException {
        writer.heading(text(locale, "accounting.pdf.incomeStatement"));
        appendSection(writer, text(locale, "accounting.pdf.revenue"), incomeStatement.get("revenue"),
                incomeStatement.get("totalRevenue"), locale);
        appendSection(writer, text(locale, "accounting.pdf.expenses"), incomeStatement.get("expenses"),
                incomeStatement.get("totalExpenses"), locale);
        writer.line(text(locale, "table.netIncome") + ": " + writer.amount(incomeStatement.get("netIncome")), true);
    }

    private void appendSection(Writer writer, String title, Object data, Object total, Locale locale)
            throws IOException {
        writer.line(title, true);
        if (data instanceof List<?> rows) {
            for (Object item : rows) {
                Map<String, Object> row = map(item);
                writer.line(row.get("accountNumber") + "  " + row.get("accountName") + "  "
                        + writer.amount(row.get("balance")), false);
            }
        }
        writer.line(text(locale, "accounting.pdf.total") + " " + title + ": " + writer.amount(total), true);
    }

    private String text(Locale locale, String key) {
        return messages.getMessage(key, null, locale);
    }

    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private static final class Writer {
        private final PDDocument document;
        private final String currency;
        private final Charset pdfCharset = Charset.forName("windows-1252");
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;

        private Writer(PDDocument document, String currency) throws IOException {
            this.document = document;
            this.currency = currency == null || currency.isBlank() ? "CAD" : currency;
            newPage();
        }

        private String amount(Object value) {
            BigDecimal amount = value instanceof BigDecimal decimal ? decimal : BigDecimal.ZERO;
            return amount.toPlainString() + " " + currency;
        }

        private void heading(String value) throws IOException {
            space();
            line(value, true);
        }

        private void space() throws IOException {
            y -= 8;
            if (y < 55) newPage();
        }

        private void line(String value, boolean emphasized) throws IOException {
            if (y < 45) newPage();
            stream.beginText();
            stream.setFont(emphasized ? bold : regular, emphasized ? 10 : 9);
            stream.newLineAtOffset(42, y);
            String printable = value == null ? "" : value.replace('\n', ' ');
            printable = new String(printable.getBytes(pdfCharset), pdfCharset);
            stream.showText(printable);
            stream.endText();
            y -= emphasized ? 15 : 12;
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - 48;
        }

        private void finish() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
