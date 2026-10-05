package com.cogitosum.service;

import com.cogitosum.entity.BankReconciliationLine;
import com.cogitosum.entity.BankReconciliationLineType;
import com.cogitosum.entity.BankReconciliationSession;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.Company;
import com.cogitosum.repository.BankReconciliationLineRepository;
import com.cogitosum.repository.BankReconciliationSessionRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankReconciliationReportPdfServiceTest {

    private final BankReconciliationSessionRepository sessions = mock(BankReconciliationSessionRepository.class);
    private final BankReconciliationLineRepository lines = mock(BankReconciliationLineRepository.class);
    private final BankReconciliationService reconciliationService = mock(BankReconciliationService.class);
    private final CurrentCompanyContext companyContext = mock(CurrentCompanyContext.class);
    private final StaticMessageSource messages = messages();

    @AfterEach
    void clearLocale() {
        org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void reportContainsReconciliationSummaryAndSelectedLineDetails() throws Exception {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(Locale.US);
        BankReconciliationSession session = session();
        Company company = new Company();
        company.setLegalName("CogitoSum Inc.");
        when(companyContext.requireCompanyId()).thenReturn(7L);
        when(companyContext.requireCompany()).thenReturn(company);
        when(sessions.findByIdAndCompanyId(31L, 7L)).thenReturn(Optional.of(session));
        when(lines.findBySessionIdOrderByTransactionDateAscIdAsc(31L)).thenReturn(List.of(line()));

        BankReconciliationReportPdfService service = new BankReconciliationReportPdfService(
                sessions, lines, reconciliationService, companyContext, messages);
        var report = service.getPdfReport(31L).orElseThrow();

        assertEquals("reconciliation-1001-2026-09-30.pdf", report.filename());
        try (var document = Loader.loadPDF(report.content())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Reconciliation Detail"));
            assertTrue(text.contains("Transfer"));
            assertTrue(text.contains("TRANSFER-15"));
            assertTrue(text.contains("25,000.00"));
            assertTrue(text.contains("Clr"));
            assertTrue(text.contains("X"));
        }
    }

    @Test
    void reportReconstructsDetailsForAnOlderReconciliation() throws Exception {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(Locale.US);
        BankReconciliationSession session = session();
        Company company = new Company();
        company.setLegalName("CogitoSum Inc.");
        when(companyContext.requireCompanyId()).thenReturn(7L);
        when(companyContext.requireCompany()).thenReturn(company);
        when(sessions.findByIdAndCompanyId(31L, 7L)).thenReturn(Optional.of(session));
        when(lines.findBySessionIdOrderByTransactionDateAscIdAsc(31L)).thenReturn(List.of());
        when(reconciliationService.recoverLegacyReportLines(session)).thenReturn(List.of(line()));

        BankReconciliationReportPdfService service = new BankReconciliationReportPdfService(
                sessions, lines, reconciliationService, companyContext, messages);
        var report = service.getPdfReport(31L).orElseThrow();

        try (var document = Loader.loadPDF(report.content())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("TRANSFER-15"));
            assertTrue(text.contains("verify them against the original statement"));
        }
    }

    private BankReconciliationSession session() {
        BankReconciliationSession session = new BankReconciliationSession();
        session.setId(31L);
        ChartOfAccount account = new ChartOfAccount();
        account.setAccountNumber("1001");
        account.setAccountName("Bank - Operating");
        session.setBankAccount(account);
        session.setStatementDate(LocalDate.of(2026, 9, 30));
        session.setOpeningBalance(new BigDecimal("35000.00"));
        session.setTransactionTotal(new BigDecimal("-25000.00"));
        session.setRegisterBalance(new BigDecimal("10000.00"));
        session.setEndingBalance(new BigDecimal("10000.00"));
        return session;
    }

    private BankReconciliationLine line() {
        BankReconciliationLine line = new BankReconciliationLine();
        line.setJournalEntryId(125L);
        line.setTransactionType(BankReconciliationLineType.TRANSFER);
        line.setTransactionDate(LocalDate.of(2026, 9, 8));
        line.setDocumentNumber("TRANSFER-15");
        line.setName("Term Deposit");
        line.setDescription("Transfer from 1001 to 1440");
        line.setAmount(new BigDecimal("-25000.00"));
        return line;
    }

    private static StaticMessageSource messages() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("reconcile.report.title", Locale.US, "Reconciliation Detail");
        source.addMessage("reconcile.report.periodEnding", Locale.US, "Period Ending {0}");
        source.addMessage("reconcile.report.type", Locale.US, "Type");
        source.addMessage("reconcile.report.date", Locale.US, "Date");
        source.addMessage("reconcile.report.number", Locale.US, "Num");
        source.addMessage("reconcile.report.name", Locale.US, "Name");
        source.addMessage("reconcile.report.cleared", Locale.US, "Clr");
        source.addMessage("reconcile.report.amount", Locale.US, "Amount");
        source.addMessage("reconcile.report.balance", Locale.US, "Balance");
        source.addMessage("reconcile.report.beginningBalance", Locale.US, "Beginning Balance");
        source.addMessage("reconcile.report.clearedTransactions", Locale.US, "Cleared Transactions");
        source.addMessage("reconcile.report.chequesAndPayments", Locale.US, "Cheques and Payments");
        source.addMessage("reconcile.report.depositsAndCredits", Locale.US, "Deposits and Credits");
        source.addMessage("reconcile.report.itemCount", Locale.US, "{0} items");
        source.addMessage("reconcile.report.sectionTotal", Locale.US, "Total");
        source.addMessage("reconcile.report.totalClearedTransactions", Locale.US, "Total Cleared Transactions");
        source.addMessage("reconcile.report.clearedBalance", Locale.US, "Cleared Balance");
        source.addMessage("reconcile.report.registerBalance", Locale.US, "Register Balance as of {0}");
        source.addMessage("reconcile.report.endingBalance", Locale.US, "Ending Balance");
        source.addMessage("reconcile.report.difference", Locale.US, "Difference");
        source.addMessage("reconcile.report.unavailable", Locale.US, "N/A");
        source.addMessage("reconcile.report.page", Locale.US, "Page {0}");
        source.addMessage("reconcile.report.legacyNoLines", Locale.US, "Not reconciled");
        source.addMessage("reconcile.report.reconstructedLines", Locale.US,
                "Transaction details were reconstructed; verify them against the original statement.");
        source.addMessage("reconcile.report.type.cheque", Locale.US, "Cheque");
        source.addMessage("reconcile.report.type.transfer", Locale.US, "Transfer");
        source.addMessage("reconcile.report.type.payment", Locale.US, "Payment");
        source.addMessage("reconcile.report.type.generalJournal", Locale.US, "General Journal");
        return source;
    }
}
