package com.cogitosum.service;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.CreditNoteRepository;
import com.cogitosum.repository.CreditNoteRefundRepository;
import com.cogitosum.repository.CreditNoteAllocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingReportServiceAgingTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CreditNoteRepository creditNoteRepository;
    @Mock private CreditNoteRefundRepository creditNoteRefundRepository;
    @Mock private CreditNoteAllocationRepository creditNoteAllocationRepository;
    @Mock private CurrentCompanyContext companyContext;
    @InjectMocks private BillingReportService service;

    @Test
    void selectedDateRebucketsCustomerRowsAndTotalsTogether() {
        when(companyContext.requireCompanyId()).thenReturn(77L);
        when(invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(77L, InvoiceStatus.SENT))
                .thenReturn(List.of(
                        invoice(1L, 11L, "Current Customer", "100.00", LocalDate.of(2026, 1, 31)),
                        invoice(2L, 22L, "Overdue Customer", "50.00", LocalDate.of(2026, 1, 1))));

        LocalDate firstDate = LocalDate.of(2026, 1, 31);
        BillingReportService.AgingReport first = service.getAgingReport(firstDate);
        assertEquals(new BigDecimal("100.00"), first.totals().get("current"));
        assertEquals(new BigDecimal("50.00"), first.totals().get("30Days"));
        assertEquals(new BigDecimal("100.00"), customer(first, "Current Customer").get("current"));
        assertEquals(new BigDecimal("50.00"), customer(first, "Overdue Customer").get("days1To30"));

        LocalDate nextDate = LocalDate.of(2026, 2, 1);
        BillingReportService.AgingReport next = service.getAgingReport(nextDate);
        assertEquals(BigDecimal.ZERO, next.totals().get("current"));
        assertEquals(new BigDecimal("100.00"), next.totals().get("30Days"));
        assertEquals(new BigDecimal("50.00"), next.totals().get("60Days"));
        assertEquals(new BigDecimal("100.00"), customer(next, "Current Customer").get("days1To30"));
        assertEquals(new BigDecimal("50.00"), customer(next, "Overdue Customer").get("days31To60"));
    }

    @Test
    void appliedCreditReducesTheTargetInvoiceAgingBalance() {
        when(companyContext.requireCompanyId()).thenReturn(77L);
        Invoice target = invoice(4L, 44L, "Allocation Customer", "100.00", LocalDate.of(2026, 1, 31));
        when(invoiceRepository.findByCompanyIdAndStatusOrderByInvoiceNumberDesc(77L, InvoiceStatus.SENT))
                .thenReturn(List.of(target));
        when(creditNoteRepository.sumTotalByCompanyIdAndInvoiceIdAndStatusBeforeDate(
                77L, 4L, com.cogitosum.entity.CreditNoteStatus.ISSUED, LocalDate.of(2026, 1, 31)))
                .thenReturn(BigDecimal.ZERO);
        when(creditNoteRefundRepository.sumByCompanyIdAndInvoiceIdBeforeDate(
                77L, 4L, LocalDate.of(2026, 1, 31))).thenReturn(BigDecimal.ZERO);
        when(creditNoteAllocationRepository.sumByCompanyIdAndTargetInvoiceIdBeforeDate(
                77L, 4L, LocalDate.of(2026, 1, 31))).thenReturn(new BigDecimal("30.00"));

        BillingReportService.AgingReport report = service.getAgingReport(LocalDate.of(2026, 1, 31));

        assertEquals(new BigDecimal("70.00"), report.totals().get("current"));
        assertEquals(new BigDecimal("70.00"), report.totals().get("totalOutstanding"));
    }

    private Invoice invoice(Long invoiceId, Long customerId, String customerName, String amount, LocalDate dueDate) {
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setBusinessName(customerName);

        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setCustomer(customer);
        invoice.setDueDate(dueDate);
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setTotalAmount(new BigDecimal(amount));
        invoice.setPaidAmount(BigDecimal.ZERO);
        return invoice;
    }

    private Map<String, Object> customer(BillingReportService.AgingReport report, String name) {
        return report.customers().stream()
                .filter(row -> name.equals(row.get("customerName")))
                .findFirst()
                .orElseThrow();
    }
}
