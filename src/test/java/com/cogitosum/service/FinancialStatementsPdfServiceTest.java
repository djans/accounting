package com.cogitosum.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialStatementsPdfServiceTest {

    @Test
    void generatesReadablePdfWithCompanyAndSelectedReportPeriods() throws Exception {
        MessageSource messages = mock(MessageSource.class);
        when(messages.getMessage(anyString(), isNull(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0) + " — Épreuve");
        FinancialStatementsPdfService service = new FinancialStatementsPdfService(messages);
        Map<String, Object> emptyTrialBalance = Map.of(
                "accounts", Map.of(), "totalDebits", BigDecimal.ZERO, "totalCredits", BigDecimal.ZERO);
        Map<String, Object> reports = Map.of(
                "trialBalanceBeforeClose", emptyTrialBalance,
                "trialBalance", emptyTrialBalance,
                "balanceSheet", Map.of("assets", List.of(), "liabilities", List.of(), "equity", List.of(),
                        "totalAssets", BigDecimal.ZERO, "totalLiabilities", BigDecimal.ZERO,
                        "totalEquity", BigDecimal.ZERO, "netIncome", BigDecimal.ZERO,
                        "totalLiabilitiesAndEquity", BigDecimal.ZERO),
                "incomeStatement", Map.of("revenue", List.of(), "expenses", List.of(),
                        "totalRevenue", BigDecimal.ZERO, "totalExpenses", BigDecimal.ZERO, "netIncome", BigDecimal.ZERO));

        byte[] bytes = service.generate(reports, "Société d'essai", "CAD",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 3, 31), Locale.FRENCH);

        assertTrue(bytes.length > 500);
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertTrue(document.getNumberOfPages() >= 1);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Société d'essai"));
            assertTrue(text.contains("Épreuve"));
        }
    }
}
