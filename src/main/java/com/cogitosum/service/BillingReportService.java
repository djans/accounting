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
import java.util.ArrayList;
import java.util.LinkedHashMap;

@Service
public class BillingReportService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    public Map<String, BigDecimal> getRevenueReport(LocalDate startDate, LocalDate endDate) {
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetweenOrderByInvoiceDateDesc(startDate, endDate);

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
        return getAgingAnalysis(LocalDate.now());
    }

    public Map<String, Object> getAgingAnalysis(LocalDate asOf) {
        LocalDate thirtyDaysAgo = asOf.minusDays(30);
        LocalDate sixtyDaysAgo = asOf.minusDays(60);
        LocalDate ninetyDaysAgo = asOf.minusDays(90);

        List<Invoice> invoices = invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.SENT);
        invoices.addAll(invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.PARTIALLY_PAID));
        invoices.addAll(invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.OVERDUE));

        BigDecimal current = BigDecimal.ZERO;
        BigDecimal days30 = BigDecimal.ZERO;
        BigDecimal days60 = BigDecimal.ZERO;
        BigDecimal days61To90 = BigDecimal.ZERO;
        BigDecimal days90Plus = BigDecimal.ZERO;

        for (Invoice invoice : invoices) {
            if (invoice.getDueDate() == null) continue;
            BigDecimal outstanding = invoice.getTotalAmount().subtract(
                invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO
            );

            if (invoice.getDueDate().isAfter(asOf) || invoice.getDueDate().isEqual(asOf)) {
                current = current.add(outstanding);
            } else if (invoice.getDueDate().isAfter(thirtyDaysAgo)) {
                days30 = days30.add(outstanding);
            } else if (invoice.getDueDate().isAfter(sixtyDaysAgo)) {
                days60 = days60.add(outstanding);
            } else if (invoice.getDueDate().isAfter(ninetyDaysAgo)) {
                days61To90 = days61To90.add(outstanding);
            } else {
                days90Plus = days90Plus.add(outstanding);
            }
        }

        Map<String, Object> agingReport = new HashMap<>();
        agingReport.put("current", current);
        agingReport.put("30Days", days30);
        agingReport.put("60Days", days60);
        agingReport.put("61To90Days", days61To90);
        agingReport.put("90DaysPlus", days90Plus);
        agingReport.put("totalOutstanding", current.add(days30).add(days60).add(days61To90).add(days90Plus));

        return agingReport;
    }

    public List<Map<String, Object>> getAgingSummary(LocalDate asOf) {
        Map<Long, Map<String, Object>> byCustomer = new LinkedHashMap<>();
        List<Invoice> invoices = new ArrayList<>(invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.SENT));
        invoices.addAll(invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.PARTIALLY_PAID));
        invoices.addAll(invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.OVERDUE));

        for (Invoice invoice : invoices) {
            if (invoice.getCustomer() == null || invoice.getDueDate() == null) continue;
            BigDecimal outstanding = invoice.getTotalAmount().subtract(
                    invoice.getPaidAmount() == null ? BigDecimal.ZERO : invoice.getPaidAmount());
            Map<String, Object> row = byCustomer.computeIfAbsent(invoice.getCustomer().getId(), id -> {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("customerName", invoice.getCustomer().getBusinessName());
                values.put("current", BigDecimal.ZERO);
                values.put("days1To30", BigDecimal.ZERO);
                values.put("days31To60", BigDecimal.ZERO);
                values.put("days61To90", BigDecimal.ZERO);
                values.put("over90", BigDecimal.ZERO);
                values.put("total", BigDecimal.ZERO);
                return values;
            });

            long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(invoice.getDueDate(), asOf);
            String bucket = overdueDays <= 0 ? "current"
                    : overdueDays <= 30 ? "days1To30"
                    : overdueDays <= 60 ? "days31To60"
                    : overdueDays <= 90 ? "days61To90"
                    : "over90";
            row.put(bucket, ((BigDecimal) row.get(bucket)).add(outstanding));
            row.put("total", ((BigDecimal) row.get("total")).add(outstanding));
        }
        return new ArrayList<>(byCustomer.values());
    }

    public Map<String, Object> getInvoiceStatusSummary() {
        Map<String, Object> summary = new HashMap<>();

        summary.put("draft", invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.DRAFT).size());
        summary.put("sent", invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.SENT).size());
        summary.put("viewed", invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.VIEWED).size());
        summary.put("partiallyPaid", invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.PARTIALLY_PAID).size());
        summary.put("paid", invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.PAID).size());
        summary.put("overdue", invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.OVERDUE).size());
        summary.put("cancelled", invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.CANCELLED).size());
        summary.put("refunded", invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.REFUNDED).size());

        return summary;
    }

    public Map<String, BigDecimal> getTaxSummary(LocalDate startDate, LocalDate endDate) {
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetweenOrderByInvoiceDateDesc(startDate, endDate);

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
