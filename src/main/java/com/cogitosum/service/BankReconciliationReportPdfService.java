package com.cogitosum.service;

import com.cogitosum.entity.BankReconciliationLine;
import com.cogitosum.entity.BankReconciliationLineType;
import com.cogitosum.entity.BankReconciliationSession;
import com.cogitosum.entity.Company;
import com.cogitosum.repository.BankReconciliationLineRepository;
import com.cogitosum.repository.BankReconciliationSessionRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class BankReconciliationReportPdfService {

    private final BankReconciliationSessionRepository sessionRepository;
    private final BankReconciliationLineRepository lineRepository;
    private final BankReconciliationService reconciliationService;
    private final CurrentCompanyContext companyContext;
    private final MessageSource messageSource;

    public BankReconciliationReportPdfService(BankReconciliationSessionRepository sessionRepository,
                                             BankReconciliationLineRepository lineRepository,
                                             BankReconciliationService reconciliationService,
                                             CurrentCompanyContext companyContext,
                                             MessageSource messageSource) {
        this.sessionRepository = sessionRepository;
        this.lineRepository = lineRepository;
        this.reconciliationService = reconciliationService;
        this.companyContext = companyContext;
        this.messageSource = messageSource;
    }

    @Transactional(readOnly = true)
    public List<BankReconciliationSession> getCompanySessions() {
        return sessionRepository.findByCompanyIdOrderByStatementDateDescIdDesc(
                companyContext.requireCompanyId());
    }

    @Transactional(readOnly = true)
    public Optional<PdfReport> getPdfReport(Long sessionId) {
        Long companyId = companyContext.requireCompanyId();
        return sessionRepository.findByIdAndCompanyId(sessionId, companyId)
                .map(session -> {
                    List<BankReconciliationLine> lines =
                            lineRepository.findBySessionIdOrderByTransactionDateAscIdAsc(sessionId);
                    boolean reconstructed = lines.isEmpty() && !session.isReportLinesCaptured();
                    if (reconstructed) {
                        lines = reconciliationService.recoverLegacyReportLines(session);
                    }
                    Company company = companyContext.requireCompany();
                    Locale locale = LocaleContextHolder.getLocale();
                    return new PdfReport(
                            render(session, lines, company, locale, reconstructed && !lines.isEmpty()),
                            filenameFor(session));
                });
    }

    private byte[] render(BankReconciliationSession session, List<BankReconciliationLine> lines,
                          Company company, Locale locale, boolean reconstructed) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream();
             ReconciliationPdfLayout layout =
                     new ReconciliationPdfLayout(document, session, company, locale, messageSource)) {
            List<BankReconciliationLine> payments = lines.stream()
                    .filter(line -> line.getAmount().signum() < 0)
                    .toList();
            List<BankReconciliationLine> deposits = lines.stream()
                    .filter(line -> line.getAmount().signum() >= 0)
                    .toList();

            layout.summaryRow(message(locale, "reconcile.report.beginningBalance"),
                    null, session.getOpeningBalance(), true);
            layout.section(message(locale, "reconcile.report.clearedTransactions"));
            appendSection(layout, payments, message(locale, "reconcile.report.chequesAndPayments"), locale);
            appendSection(layout, deposits, message(locale, "reconcile.report.depositsAndCredits"), locale);

            BigDecimal activity = session.getTransactionTotal();
            BigDecimal clearedBalance = session.getOpeningBalance().add(activity);
            layout.rule();
            layout.summaryRow(message(locale, "reconcile.report.totalClearedTransactions"),
                    activity, activity, true);
            layout.summaryRow(message(locale, "reconcile.report.clearedBalance"),
                    activity, clearedBalance, true);
            layout.summaryRow(message(locale, "reconcile.report.registerBalance", formatDate(session.getStatementDate(), locale)),
                    null, session.getRegisterBalance(), false);
            layout.summaryRow(message(locale, "reconcile.report.endingBalance"),
                    null, session.getEndingBalance(), true);
            layout.summaryRow(message(locale, "reconcile.report.difference"),
                    null, session.getEndingBalance().subtract(clearedBalance), false);
            if (reconstructed) {
                layout.note(message(locale, "reconcile.report.reconstructedLines"));
            } else if (lines.isEmpty() && !session.isReportLinesCaptured()) {
                layout.note(message(locale, "reconcile.report.legacyNoLines"));
            }
            layout.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate the reconciliation report PDF", exception);
        }
    }

    private void appendSection(ReconciliationPdfLayout layout, List<BankReconciliationLine> lines,
                               String title, Locale locale) throws IOException {
        if (lines.isEmpty()) {
            return;
        }
        layout.section(title + " - " + message(locale, "reconcile.report.itemCount", lines.size()));
        BigDecimal runningBalance = BigDecimal.ZERO;
        for (BankReconciliationLine line : lines) {
            runningBalance = runningBalance.add(line.getAmount());
            layout.transactionRow(
                    message(locale, typeKey(line.getTransactionType())),
                    formatDate(line.getTransactionDate(), locale),
                    line.getDocumentNumber(),
                    line.getName(),
                    line.getAmount(),
                    runningBalance);
        }
        layout.summaryRow(message(locale, "reconcile.report.sectionTotal"),
                runningBalance, runningBalance, true);
    }

    private String typeKey(BankReconciliationLineType type) {
        return switch (type) {
            case CHEQUE -> "reconcile.report.type.cheque";
            case TRANSFER -> "reconcile.report.type.transfer";
            case PAYMENT -> "reconcile.report.type.payment";
            case GENERAL_JOURNAL -> "reconcile.report.type.generalJournal";
        };
    }

    private String message(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    private String formatDate(LocalDate date, Locale locale) {
        if (date == null) {
            return "";
        }
        String pattern = "fr".equals(locale.getLanguage()) ? "dd/MM/uuuu" : "MM/dd/uuuu";
        return date.format(DateTimeFormatter.ofPattern(pattern, locale));
    }

    private String filenameFor(BankReconciliationSession session) {
        String accountNumber = session.getBankAccount().getAccountNumber();
        String safeAccount = accountNumber == null ? "account"
                : accountNumber.replaceAll("[^A-Za-z0-9._-]+", "-");
        return "reconciliation-" + safeAccount + "-" + session.getStatementDate() + ".pdf";
    }

    public record PdfReport(byte[] content, String filename) {
    }

    private static final class ReconciliationPdfLayout implements AutoCloseable {

        private static final float LEFT = 42;
        private static final float RIGHT = 570;
        private static final float BOTTOM = 48;
        private static final float LINE_HEIGHT = 16;
        private static final float[] COLUMNS = {42, 126, 196, 243, 345, 402, 484};
        private static final float[] COLUMN_WIDTHS = {82, 68, 45, 100, 55, 80, 86};

        private final PDDocument document;
        private final BankReconciliationSession session;
        private final Company company;
        private final Locale locale;
        private final MessageSource messageSource;
        private final PDType1Font regular = new PDType1Font(FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(FontName.HELVETICA_BOLD);
        private final NumberFormat currencyFormat;
        private final DateTimeFormatter timeFormat;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        private ReconciliationPdfLayout(PDDocument document, BankReconciliationSession session,
                                       Company company, Locale locale,
                                       MessageSource messageSource) throws IOException {
            this.document = document;
            this.session = session;
            this.company = company;
            this.locale = locale;
            this.messageSource = messageSource;
            currencyFormat = NumberFormat.getCurrencyInstance(locale);
            currencyFormat.setCurrency(java.util.Currency.getInstance("CAD"));
            currencyFormat.setMinimumFractionDigits(2);
            currencyFormat.setMaximumFractionDigits(2);
            timeFormat = DateTimeFormatter.ofPattern(
                    "fr".equals(locale.getLanguage()) ? "HH:mm" : "h:mm a", locale);
            newPage();
        }

        private void transactionRow(String type, String date, String number, String name,
                                   BigDecimal amount, BigDecimal runningBalance) throws IOException {
            ensureRowSpace();
            text(type, COLUMNS[0], y, regular, 9, COLUMN_WIDTHS[0]);
            text(date, COLUMNS[1], y, regular, 9, COLUMN_WIDTHS[1]);
            text(number, COLUMNS[2], y, regular, 9, COLUMN_WIDTHS[2]);
            text(name, COLUMNS[3], y, regular, 9, COLUMN_WIDTHS[3]);
            text("X", COLUMNS[4], y, regular, 9, COLUMN_WIDTHS[4]);
            rightText(money(amount), COLUMNS[5] + COLUMN_WIDTHS[5], y, regular, 9, COLUMN_WIDTHS[5]);
            rightText(money(runningBalance), COLUMNS[6] + COLUMN_WIDTHS[6], y, regular, 9, COLUMN_WIDTHS[6]);
            y -= LINE_HEIGHT;
        }

        private void summaryRow(String label, BigDecimal amount, BigDecimal balance,
                               boolean emphasized) throws IOException {
            ensureRowSpace();
            PDType1Font font = emphasized ? bold : regular;
            text(label, LEFT, y, font, 9, COLUMNS[5] - LEFT - 8);
            if (amount != null) {
                rightText(money(amount), COLUMNS[5] + COLUMN_WIDTHS[5], y, font, 9, COLUMN_WIDTHS[5]);
            }
            if (balance != null) {
                rightText(money(balance), COLUMNS[6] + COLUMN_WIDTHS[6], y, font, 9, COLUMN_WIDTHS[6]);
            } else {
                text(message("reconcile.report.unavailable"), COLUMNS[6], y, regular, 8, COLUMN_WIDTHS[6]);
            }
            y -= LINE_HEIGHT;
        }

        private void section(String heading) throws IOException {
            ensureRowSpace();
            y -= 3;
            text(heading, LEFT, y, bold, 10, RIGHT - LEFT);
            y -= LINE_HEIGHT;
        }

        private void note(String value) throws IOException {
            ensureRowSpace();
            text(value, LEFT, y, regular, 8, RIGHT - LEFT);
            y -= LINE_HEIGHT;
        }

        private void rule() throws IOException {
            ensureRowSpace();
            stream.moveTo(LEFT, y + 5);
            stream.lineTo(RIGHT, y + 5);
            stream.stroke();
            y -= 5;
        }

        private void ensureRowSpace() throws IOException {
            if (y < BOTTOM + LINE_HEIGHT) {
                newPage();
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
            y = 756;

            String companyName = company.getLegalName() == null || company.getLegalName().isBlank()
                    ? company.getName()
                    : company.getLegalName();
            text(companyName, LEFT, y, bold, 10, RIGHT - LEFT);
            y -= 20;
            text(message("reconcile.report.title"), LEFT, y, bold, 16, RIGHT - LEFT);
            y -= 21;
            text(session.getBankAccount().getAccountName(), LEFT, y, bold, 11, RIGHT - LEFT);
            y -= 15;
            text(message("reconcile.report.periodEnding", formatDate(session.getStatementDate())),
                    LEFT, y, regular, 9, RIGHT - LEFT);
            rightText(formatDate(LocalDate.now())
                            + " " + LocalDateTime.now().format(timeFormat),
                    RIGHT, 756, regular, 8, 135);
            y -= 16;
            rule();
            tableHeader();
        }

        private void tableHeader() throws IOException {
            String[] labels = {
                    message("reconcile.report.type"),
                    message("reconcile.report.date"),
                    message("reconcile.report.number"),
                    message("reconcile.report.name"),
                    message("reconcile.report.cleared"),
                    message("reconcile.report.amount"),
                    message("reconcile.report.balance")
            };
            for (int i = 0; i < labels.length; i++) {
                text(labels[i], COLUMNS[i], y, bold, 8, COLUMN_WIDTHS[i]);
            }
            y -= 10;
            rule();
        }

        private void footer() throws IOException {
            stream.moveTo(LEFT, 36);
            stream.lineTo(RIGHT, 36);
            stream.stroke();
            rightText(message("reconcile.report.page", pageNumber), RIGHT, 22, regular, 8, 80);
        }

        private String formatDate(LocalDate date) {
            if (date == null) {
                return "";
            }
            String pattern = "fr".equals(locale.getLanguage()) ? "dd/MM/uuuu" : "MM/dd/uuuu";
            return date.format(DateTimeFormatter.ofPattern(pattern, locale));
        }

        private String money(BigDecimal amount) {
            if (amount == null) {
                return "";
            }
            return currencyFormat.format(amount)
                    .replace('\u00a0', ' ')
                    .replace('\u202f', ' ');
        }

        private String message(String key, Object... args) {
            return messageSource.getMessage(key, args, locale);
        }

        private void text(String value, float x, float baseline, PDType1Font font,
                          float size, float maxWidth) throws IOException {
            String safe = fit(InvoicePdfService.safeText(value), font, size, maxWidth);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(safe);
            stream.endText();
        }

        private void rightText(String value, float rightEdge, float baseline, PDType1Font font,
                               float size, float maxWidth) throws IOException {
            String safe = fit(InvoicePdfService.safeText(value), font, size, maxWidth);
            float width = font.getStringWidth(safe) / 1000 * size;
            text(safe, rightEdge - width, baseline, font, size, maxWidth);
        }

        private String fit(String value, PDType1Font font, float size, float maxWidth) throws IOException {
            if (value == null || value.isBlank()) {
                return "";
            }
            String candidate = value;
            while (font.getStringWidth(candidate) / 1000 * size > maxWidth && !candidate.isEmpty()) {
                candidate = candidate.substring(0, candidate.length() - 1);
            }
            return candidate.stripTrailing();
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
