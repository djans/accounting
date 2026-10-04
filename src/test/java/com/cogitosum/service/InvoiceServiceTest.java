package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CurrentCompanyContext companyContext;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService();
        ReflectionTestUtils.setField(invoiceService, "invoiceRepository", invoiceRepository);
        ReflectionTestUtils.setField(invoiceService, "companyContext", companyContext);
        when(companyContext.requireCompanyId()).thenReturn(1L);
    }

    @Test
    void unpaidInvoicesIncludesOnlyInvoicesWithAnOutstandingBalance() {
        Invoice sent = invoice("INV-1", InvoiceStatus.SENT, "100.00", null);
        Invoice partiallyPaid = invoice("INV-2", InvoiceStatus.PARTIALLY_PAID, "100.00", "40.00");
        Invoice paidByAmount = invoice("INV-3", InvoiceStatus.SENT, "100.00", "100.00");
        Invoice paidByStatus = invoice("INV-4", InvoiceStatus.PAID, "100.00", "100.00");
        Invoice draft = invoice("INV-5", InvoiceStatus.DRAFT, "100.00", "0.00");
        Invoice cancelled = invoice("INV-6", InvoiceStatus.CANCELLED, "100.00", "0.00");
        Invoice refunded = invoice("INV-7", InvoiceStatus.REFUNDED, "100.00", "0.00");
        when(invoiceRepository.findAllByCompanyIdOrderByInvoiceNumberDesc(1L))
                .thenReturn(List.of(sent, partiallyPaid, paidByAmount, paidByStatus, draft, cancelled, refunded));

        assertEquals(List.of(sent, partiallyPaid), invoiceService.getUnpaidInvoices());
        assertEquals(new BigDecimal("60.00"), invoiceService.getOutstandingAmount(partiallyPaid));
    }

    private Invoice invoice(String number, InvoiceStatus status, String total, String paid) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(number);
        invoice.setStatus(status);
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setPaidAmount(paid == null ? null : new BigDecimal(paid));
        return invoice;
    }
}
