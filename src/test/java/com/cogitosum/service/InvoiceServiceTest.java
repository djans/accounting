package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.Company;
import com.cogitosum.entity.Customer;
import com.cogitosum.entity.LineItem;
import com.cogitosum.repository.CustomerRepository;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.CreditNoteRepository;
import com.cogitosum.repository.CreditNoteRefundRepository;
import com.cogitosum.repository.CreditNoteAllocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CreditNoteRepository creditNoteRepository;
    @Mock private CreditNoteRefundRepository creditNoteRefundRepository;
    @Mock private CreditNoteAllocationRepository creditNoteAllocationRepository;
    @Mock private InvoicePostingService invoicePostingService;
    @Mock private CurrentCompanyContext companyContext;
    @Mock private CustomerRepository customerRepository;
    @Mock private TaxCodeService taxCodeService;
    @Mock private InvoicePdfService invoicePdfService;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService();
        ReflectionTestUtils.setField(invoiceService, "invoiceRepository", invoiceRepository);
        ReflectionTestUtils.setField(invoiceService, "creditNoteRepository", creditNoteRepository);
        ReflectionTestUtils.setField(invoiceService, "creditNoteRefundRepository", creditNoteRefundRepository);
        ReflectionTestUtils.setField(invoiceService, "creditNoteAllocationRepository", creditNoteAllocationRepository);
        ReflectionTestUtils.setField(invoiceService, "invoicePostingService", invoicePostingService);
        ReflectionTestUtils.setField(invoiceService, "companyContext", companyContext);
        ReflectionTestUtils.setField(invoiceService, "customerRepository", customerRepository);
        ReflectionTestUtils.setField(invoiceService, "taxCodeService", taxCodeService);
        ReflectionTestUtils.setField(invoiceService, "invoicePdfService", invoicePdfService);
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

    @Test
    void refundedCreditNoteDoesNotRemainAsACustomerCreditBalance() {
        Invoice invoice = invoice("INV-REFUNDED-CREDIT", InvoiceStatus.CREDITED, "100.00", "100.00");
        invoice.setId(12L);
        when(creditNoteRepository.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                1L, 12L, com.cogitosum.entity.CreditNoteStatus.ISSUED))
                .thenReturn(new BigDecimal("25.00"));
        when(creditNoteRefundRepository.sumByCompanyIdAndInvoiceId(1L, 12L))
                .thenReturn(new BigDecimal("25.00"));
        when(creditNoteAllocationRepository.sumByCompanyIdAndSourceInvoiceId(1L, 12L))
                .thenReturn(BigDecimal.ZERO);
        when(creditNoteAllocationRepository.sumByCompanyIdAndTargetInvoiceId(1L, 12L))
                .thenReturn(BigDecimal.ZERO);

        assertEquals(new BigDecimal("0.00"), invoiceService.getOutstandingAmount(invoice));
    }

    @Test
    void allocatedCreditReducesTargetInvoiceAndRestoresSourceInvoiceCredit() {
        Invoice source = invoice("INV-SOURCE", InvoiceStatus.CREDITED, "100.00", "100.00");
        source.setId(20L);
        Invoice target = invoice("INV-TARGET", InvoiceStatus.PAID, "80.00", "0.00");
        target.setId(21L);
        when(creditNoteRepository.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                1L, 20L, com.cogitosum.entity.CreditNoteStatus.ISSUED))
                .thenReturn(new BigDecimal("30.00"));
        when(creditNoteRefundRepository.sumByCompanyIdAndInvoiceId(1L, 20L))
                .thenReturn(BigDecimal.ZERO);
        when(creditNoteAllocationRepository.sumByCompanyIdAndSourceInvoiceId(1L, 20L))
                .thenReturn(new BigDecimal("30.00"));
        when(creditNoteAllocationRepository.sumByCompanyIdAndTargetInvoiceId(1L, 20L))
                .thenReturn(BigDecimal.ZERO);
        when(creditNoteRepository.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                1L, 21L, com.cogitosum.entity.CreditNoteStatus.ISSUED))
                .thenReturn(BigDecimal.ZERO);
        when(creditNoteRefundRepository.sumByCompanyIdAndInvoiceId(1L, 21L))
                .thenReturn(BigDecimal.ZERO);
        when(creditNoteAllocationRepository.sumByCompanyIdAndSourceInvoiceId(1L, 21L))
                .thenReturn(BigDecimal.ZERO);
        when(creditNoteAllocationRepository.sumByCompanyIdAndTargetInvoiceId(1L, 21L))
                .thenReturn(new BigDecimal("30.00"));

        assertEquals(new BigDecimal("0.00"), invoiceService.getOutstandingAmount(source));
        assertEquals(new BigDecimal("50.00"), invoiceService.getOutstandingAmount(target));
    }

    @Test
    void cannotCancelAnInvoiceWhileItHasAnActiveCreditAllocation() {
        Invoice invoice = invoice("INV-ALLOCATED", InvoiceStatus.PARTIALLY_PAID, "100.00", "0.00");
        invoice.setId(22L);
        when(invoiceRepository.findLockedByIdAndCompanyId(22L, 1L)).thenReturn(Optional.of(invoice));
        when(creditNoteAllocationRepository.existsByCompanyIdAndTargetInvoiceIdAndReversalDateIsNull(
                1L, 22L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> invoiceService.cancelInvoice(22L, "Incorrect invoice"));
        verify(invoicePostingService, never()).reverseInvoice(invoice, "Invoice cancelled: Incorrect invoice");
    }

    @Test
    void issuedInvoicesRequireCancellationReasonAndCannotBeDeleted() {
        Invoice invoice = invoice("INV-8", InvoiceStatus.SENT, "100.00", "0.00");
        when(invoiceRepository.findLockedByIdAndCompanyId(8L, 1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        assertThrows(IllegalArgumentException.class, () -> invoiceService.cancelInvoice(8L, " "));
        Invoice cancelled = invoiceService.cancelInvoice(8L, "Duplicate invoice");

        assertEquals(InvoiceStatus.CANCELLED, cancelled.getStatus());
        assertEquals("Duplicate invoice", cancelled.getCancellationReason());
        assertNotNull(cancelled.getCancelledAt());
        verify(invoicePostingService).reverseInvoice(invoice, "Invoice cancelled: Duplicate invoice");

        when(invoiceRepository.findByIdAndCompanyId(8L, 1L)).thenReturn(Optional.of(cancelled));
        assertThrows(IllegalStateException.class, () -> invoiceService.deleteInvoice(8L));
        verify(invoiceRepository, never()).delete(invoice);
    }

    @Test
    void createsFinalInvoiceAsIssuedAndStoresItsSelectedPdf() {
        Company company = new Company();
        Customer customer = new Customer();
        customer.setId(3L);
        LineItem line = new LineItem();
        line.setDescription("Consulting");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("100.00"));
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-12");
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setCustomer(customer);
        invoice.setLineItems(List.of(line));
        when(companyContext.requireCompany()).thenReturn(company);
        when(customerRepository.findByIdAndCompanyId(3L, 1L)).thenReturn(Optional.of(customer));
        when(taxCodeService.getAllCodes()).thenReturn(List.of());
        when(invoiceRepository.save(ArgumentMatchers.any(Invoice.class))).thenAnswer(invocation -> {
            Invoice saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });
        when(invoiceRepository.findByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(invoice));
        byte[] pdf = "%PDF-final".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(invoicePdfService.createInvoicePdf(invoice, "standard")).thenReturn(pdf);

        Invoice created = invoiceService.createInvoice(invoice, "FED", true, "standard");

        assertEquals(InvoiceStatus.SENT, created.getStatus());
        verify(invoicePostingService).postInvoice(invoice);
        verify(invoicePdfService).storeFinalPdf(12L, pdf);
    }

    @Test
    void finalizingDraftPostsItAndStoresTheSelectedPdfTemplate() {
        Invoice invoice = invoice("INV-21", InvoiceStatus.DRAFT, "100.00", "0.00");
        invoice.setId(21L);
        when(invoiceRepository.findByIdAndCompanyId(21L, 1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        byte[] pdf = "%PDF-selected".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(invoicePdfService.createInvoicePdf(invoice, "french-template")).thenReturn(pdf);

        Invoice finalized = invoiceService.finalizeInvoice(21L, "french-template");

        assertEquals(InvoiceStatus.SENT, finalized.getStatus());
        verify(invoicePostingService).postInvoice(invoice);
        verify(invoicePdfService).createInvoicePdf(invoice, "french-template");
        verify(invoicePdfService).storeFinalPdf(21L, pdf);
    }

    @Test
    void deletesDraftInvoiceAndReleasesItsNumber() {
        Invoice draft = invoice("INV-22", InvoiceStatus.DRAFT, "100.00", "0.00");
        when(invoiceRepository.findByIdAndCompanyId(22L, 1L)).thenReturn(Optional.of(draft));

        invoiceService.deleteInvoice(22L);

        verify(invoicePostingService).reverseInvoice(draft, "Invoice deleted");
        verify(invoiceRepository).delete(draft);
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
