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

    @Autowired
    private CurrentCompanyContext companyContext;

    public Map<String, BigDecimal> getRevenueReport(LocalDate startDate, LocalDate endDate) {
        List<Invoice> invoices = invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                companyContext.requireCompanyId(), startDate, endDate);

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
        return getAgingReport(asOf).totals();
    }

    public List<Map<String, Object>> getAgingSummary(LocalDate asOf) {
        return getAgingReport(asOf).customers();
    }

    public AgingReport getAgingReport(LocalDate asOf) {
        Map<Long, Map<String, Object>> byCustomer = new LinkedHashMap<>();
        Long companyId = companyContext.requireCompanyId();
        List<Invoice> invoices = new ArrayList<>(invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(companyId, InvoiceStatus.SENT));
        invoices.addAll(invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(companyId, InvoiceStatus.PARTIALLY_PAID));
        invoices.addAll(invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(companyId, InvoiceStatus.OVERDUE));

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
            addToAmount(row, bucket, outstanding);
            addToAmount(row, "total", outstanding);
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("current", BigDecimal.ZERO);
        totals.put("30Days", BigDecimal.ZERO);
        totals.put("60Days", BigDecimal.ZERO);
        totals.put("61To90Days", BigDecimal.ZERO);
        totals.put("90DaysPlus", BigDecimal.ZERO);
        totals.put("totalOutstanding", BigDecimal.ZERO);

        for (Map<String, Object> row : byCustomer.values()) {
            addToAmount(totals, "current", (BigDecimal) row.get("current"));
            addToAmount(totals, "30Days", (BigDecimal) row.get("days1To30"));
            addToAmount(totals, "60Days", (BigDecimal) row.get("days31To60"));
            addToAmount(totals, "61To90Days", (BigDecimal) row.get("days61To90"));
            addToAmount(totals, "90DaysPlus", (BigDecimal) row.get("over90"));
            addToAmount(totals, "totalOutstanding", (BigDecimal) row.get("total"));
        }
        return new AgingReport(totals, new ArrayList<>(byCustomer.values()));
    }

    private void addToAmount(Map<String, Object> values, String key, BigDecimal amount) {
        values.put(key, ((BigDecimal) values.get(key)).add(amount));
    }

    public record AgingReport(Map<String, Object> totals, List<Map<String, Object>> customers) {
    }

    public Map<String, Object> getInvoiceStatusSummary() {
        Map<String, Object> summary = new HashMap<>();

        Long companyId = companyContext.requireCompanyId();
        summary.put("draft", invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(companyId, InvoiceStatus.DRAFT).size());
        summary.put("sent", invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(companyId, InvoiceStatus.SENT).size());
        summary.put("viewed", invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(companyId, InvoiceStatus.VIEWED).size());
        summary.put("partiallyPaid", invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(companyId, InvoiceStatus.PARTIALLY_PAID).size());
        summary.put("paid", invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(companyId, InvoiceStatus.PAID).size());
        summary.put("overdue", invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(companyId, InvoiceStatus.OVERDUE).size());
        summary.put("cancelled", invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(companyId, InvoiceStatus.CANCELLED).size());
        summary.put("refunded", invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(companyId, InvoiceStatus.REFUNDED).size());

        return summary;
    }

    public Map<String, BigDecimal> getTaxSummary(LocalDate startDate, LocalDate endDate) {
        List<Invoice> invoices = invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                companyContext.requireCompanyId(), startDate, endDate);

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
