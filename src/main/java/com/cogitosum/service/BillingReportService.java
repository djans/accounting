package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BillingReportService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    public Map<String, BigDecimal> getRevenueReport(LocalDate startDate, LocalDate endDate) {
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(startDate, endDate);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;

        for (Invoice invoice : invoices) {
            if (invoice.getStatus() != InvoiceStatus.CANCELLED) {
                totalRevenue = totalRevenue.add(invoice.getTotalAmount());
                totalPaid = totalPaid.add(invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO);
                totalOutstanding = totalOutstanding.add(
                    invoice.getTotalAmount().subtract(invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO)
                );
            }
        }

        Map<String, BigDecimal> report = new HashMap<>();
        report.put("totalRevenue", totalRevenue);
        report.put("totalPaid", totalPaid);
        report.put("totalOutstanding", totalOutstanding);
        report.put("invoiceCount", BigDecimal.valueOf(invoices.size()));

        return report;
    }

    public Map<String, Object> getAgingAnalysis() {
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(30);
        LocalDate sixtyDaysAgo = today.minusDays(60);
        LocalDate ninetyDaysAgo = today.minusDays(90);

        List<Invoice> invoices = invoiceRepository.findByStatus(InvoiceStatus.SENT);
        invoices.addAll(invoiceRepository.findByStatus(InvoiceStatus.PARTIALLY_PAID));

        BigDecimal current = BigDecimal.ZERO;
        BigDecimal days30 = BigDecimal.ZERO;
        BigDecimal days60 = BigDecimal.ZERO;
        BigDecimal days90Plus = BigDecimal.ZERO;

        for (Invoice invoice : invoices) {
            BigDecimal outstanding = invoice.getTotalAmount().subtract(
                invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO
            );

            if (invoice.getDueDate().isAfter(today) || invoice.getDueDate().isEqual(today)) {
                current = current.add(outstanding);
            } else if (invoice.getDueDate().isAfter(thirtyDaysAgo)) {
                days30 = days30.add(outstanding);
            } else if (invoice.getDueDate().isAfter(sixtyDaysAgo)) {
                days60 = days60.add(outstanding);
            } else if (invoice.getDueDate().isAfter(ninetyDaysAgo)) {
                days90Plus = days90Plus.add(outstanding);
            } else {
                days90Plus = days90Plus.add(outstanding);
            }
        }

        Map<String, Object> agingReport = new HashMap<>();
        agingReport.put("current", current);
        agingReport.put("30Days", days30);
        agingReport.put("60Days", days60);
        agingReport.put("90DaysPlus", days90Plus);
        agingReport.put("totalOutstanding", current.add(days30).add(days60).add(days90Plus));

        return agingReport;
    }

    public Map<String, Object> getInvoiceStatusSummary() {
        Map<String, Object> summary = new HashMap<>();

        summary.put("draft", invoiceRepository.findByStatus(InvoiceStatus.DRAFT).size());
        summary.put("sent", invoiceRepository.findByStatus(InvoiceStatus.SENT).size());
        summary.put("viewed", invoiceRepository.findByStatus(InvoiceStatus.VIEWED).size());
        summary.put("partiallyPaid", invoiceRepository.findByStatus(InvoiceStatus.PARTIALLY_PAID).size());
        summary.put("paid", invoiceRepository.findByStatus(InvoiceStatus.PAID).size());
        summary.put("overdue", invoiceRepository.findByStatus(InvoiceStatus.OVERDUE).size());
        summary.put("cancelled", invoiceRepository.findByStatus(InvoiceStatus.CANCELLED).size());
        summary.put("refunded", invoiceRepository.findByStatus(InvoiceStatus.REFUNDED).size());

        return summary;
    }

    public Map<String, BigDecimal> getTaxSummary(LocalDate startDate, LocalDate endDate) {
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(startDate, endDate);

        BigDecimal totalGst = BigDecimal.ZERO;
        BigDecimal totalHst = BigDecimal.ZERO;
        BigDecimal totalQst = BigDecimal.ZERO;

        for (Invoice invoice : invoices) {
            if (invoice.getStatus() != InvoiceStatus.CANCELLED) {
                totalGst = totalGst.add(invoice.getGstAmount() != null ? invoice.getGstAmount() : BigDecimal.ZERO);
                totalHst = totalHst.add(invoice.getHstAmount() != null ? invoice.getHstAmount() : BigDecimal.ZERO);
                totalQst = totalQst.add(invoice.getQstAmount() != null ? invoice.getQstAmount() : BigDecimal.ZERO);
            }
        }

        Map<String, BigDecimal> taxReport = new HashMap<>();
        taxReport.put("totalGst", totalGst);
        taxReport.put("totalHst", totalHst);
        taxReport.put("totalQst", totalQst);
        taxReport.put("totalTax", totalGst.add(totalHst).add(totalQst));

        return taxReport;
    }
}

