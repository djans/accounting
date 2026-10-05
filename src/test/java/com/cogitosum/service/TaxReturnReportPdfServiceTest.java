package com.cogitosum.service;

import com.cogitosum.dto.TaxReturnRowDTO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TaxReturnReportPdfServiceTest {

    @Test
    void rendersLocalizedReturnRowsAndReconstructionNotice() throws Exception {
        StaticMessageSource messages = messages();
        TaxReturnReportPdfService service = new TaxReturnReportPdfService(messages);
        TaxFilingService.TaxReturnReportData english = report(true, List.of(
                new TaxReturnRowDTO("tax.detail.returnLine101", "101",
                        new BigDecimal("1234.50"), null, false, false),
                new TaxReturnRowDTO("tax.detail.returnLine105", "105",
                        null, new BigDecimal("1234.50"), true, false)));

        try (var document = Loader.loadPDF(service.create(english, Locale.US))) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("GST/HST/QST Return"));
            assertTrue(text.contains("Example Company"));
            assertTrue(text.contains("July through September 2026"));
            assertTrue(text.contains("Accrual basis"));
            assertTrue(text.contains("Sales and other revenue"));
            assertTrue(text.contains("101"));
            assertTrue(text.contains("1,234.50"));
            assertTrue(text.contains("Values reconstructed"));
        }

        TaxFilingService.TaxReturnReportData french = report(false, List.of(
                new TaxReturnRowDTO("tax.detail.returnLine101", "101",
                        new BigDecimal("1234.50"), null, false, false)));
        try (var document = Loader.loadPDF(service.create(french, Locale.CANADA_FRENCH))) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Declaration de TPS/TVH/TVQ"));
            assertTrue(text.contains("juillet a septembre 2026"));
            assertTrue(text.contains("Comptabilite d'exercice"));
            assertTrue(text.contains("Ventes et autres revenus"));
        }
    }

    @Test
    void startsNewPagesInsteadOfClippingLongReturn() throws Exception {
        List<TaxReturnRowDTO> rows = new ArrayList<>();
        for (int index = 0; index < 80; index++) {
            rows.add(new TaxReturnRowDTO("tax.detail.returnLine101", "101",
                    BigDecimal.ZERO, null, false, false));
        }
        TaxReturnReportPdfService service = new TaxReturnReportPdfService(messages());
        try (var document = Loader.loadPDF(service.create(report(false, rows), Locale.US))) {
            assertTrue(document.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Page 1"));
            assertTrue(text.contains("Page 2"));
            assertTrue(text.contains("0.00"));
        }
    }

    private TaxFilingService.TaxReturnReportData report(
            boolean reconstructed, List<TaxReturnRowDTO> rows) {
        return new TaxFilingService.TaxReturnReportData(
                "Example Company", "Canada Revenue Agency",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30), rows, reconstructed);
    }

    private StaticMessageSource messages() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("tax.report.title", Locale.US, "GST/HST/QST Return");
        messages.addMessage("tax.report.title", Locale.CANADA_FRENCH, "Déclaration de TPS/TVH/TVQ");
        messages.addMessage("tax.report.basis", Locale.US, "Accrual basis");
        messages.addMessage("tax.report.basis", Locale.CANADA_FRENCH, "Comptabilité d'exercice");
        messages.addMessage("tax.report.period.single", Locale.US, "{0}");
        messages.addMessage("tax.report.period.single", Locale.CANADA_FRENCH, "{0}");
        messages.addMessage("tax.report.period", Locale.US, "{0} through {1}");
        messages.addMessage("tax.report.period", Locale.CANADA_FRENCH, "{0} à {1}");
        messages.addMessage("tax.report.line", Locale.US, "Line");
        messages.addMessage("tax.report.line", Locale.CANADA_FRENCH, "Ligne");
        messages.addMessage("tax.report.description", Locale.US, "Description");
        messages.addMessage("tax.report.description", Locale.CANADA_FRENCH, "Description");
        messages.addMessage("tax.report.amount", Locale.US, "Amount");
        messages.addMessage("tax.report.amount", Locale.CANADA_FRENCH, "Montant");
        messages.addMessage("tax.report.balance", Locale.US, "Balance");
        messages.addMessage("tax.report.balance", Locale.CANADA_FRENCH, "Solde");
        messages.addMessage("tax.report.page", Locale.US, "Page {0}");
        messages.addMessage("tax.report.page", Locale.CANADA_FRENCH, "Page {0}");
        messages.addMessage("tax.report.reconstructed", Locale.US, "Values reconstructed");
        messages.addMessage("tax.report.reconstructed", Locale.CANADA_FRENCH, "Valeurs reconstituées");
        messages.addMessage("tax.detail.returnLine101", Locale.US, "Sales and other revenue");
        messages.addMessage("tax.detail.returnLine101", Locale.CANADA_FRENCH, "Ventes et autres revenus");
        messages.addMessage("tax.detail.returnLine105", Locale.US, "Total GST/HST");
        messages.addMessage("tax.detail.returnLine105", Locale.CANADA_FRENCH, "Total TPS/TVH");
        return messages;
    }
}
