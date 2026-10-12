package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteAllocation;
import com.cogitosum.entity.CreditNoteStatus;
import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.repository.CreditNoteAllocationRepository;
import com.cogitosum.repository.CreditNoteRepository;
import com.cogitosum.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditNoteAllocationServiceTest {

    @Mock private CreditNoteAllocationRepository allocations;
    @Mock private CreditNoteRepository creditNotes;
    @Mock private InvoiceRepository invoices;
    @Mock private InvoiceService invoiceService;
    @Mock private CreditNoteAvailableCreditService availableCredit;
    @Mock private CurrentCompanyContext companyContext;

    private CreditNoteAllocationService service;
    private CreditNote creditNote;
    private Invoice sourceInvoice;
    private Invoice targetInvoice;
    private Customer customer;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        service = new CreditNoteAllocationService(
                allocations, creditNotes, invoices, invoiceService, availableCredit, companyContext);
        when(companyContext.requireCompanyId()).thenReturn(1L);
        customer = new Customer();
        customer.setId(3L);
        sourceInvoice = invoice(10L, "INV-10", customer, InvoiceStatus.CREDITED, "100.00", "100.00");
        targetInvoice = invoice(20L, "INV-20", customer, InvoiceStatus.SENT, "80.00", "0.00");
        creditNote = new CreditNote();
        creditNote.setId(5L);
        creditNote.setStatus(CreditNoteStatus.ISSUED);
        creditNote.setCreditNoteDate(LocalDate.of(2026, 5, 1));
        creditNote.setTotalAmount(new BigDecimal("30.00"));
        creditNote.setInvoice(sourceInvoice);
        date = LocalDate.of(2026, 5, 2);
    }

    @Test
    void allocatesAvailableCreditToAnotherInvoiceAndUpdatesItsStatus() {
        when(creditNotes.findLockedByIdAndCompanyId(5L, 1L)).thenReturn(Optional.of(creditNote));
        when(invoices.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(sourceInvoice));
        when(invoices.findLockedByIdAndCompanyId(20L, 1L)).thenReturn(Optional.of(targetInvoice));
        when(availableCredit.getAvailableAmount(creditNote, sourceInvoice, 1L))
                .thenReturn(new BigDecimal("30.00"));
        when(invoiceService.getOutstandingAmount(targetInvoice)).thenReturn(new BigDecimal("30.00"));
        when(companyContext.requireCompany()).thenReturn(new Company());
        when(allocations.save(any(CreditNoteAllocation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreditNoteAllocation allocation = service.allocate(
                5L, 20L, new BigDecimal("30.00"), date);

        assertEquals(new BigDecimal("30.00"), allocation.getAmount());
        assertEquals(targetInvoice, allocation.getTargetInvoice());
        assertEquals(InvoiceStatus.PAID, targetInvoice.getStatus());
        verify(invoices).save(targetInvoice);
    }

    @Test
    void rejectsAllocationAboveTheAvailableCustomerCredit() {
        when(creditNotes.findLockedByIdAndCompanyId(5L, 1L)).thenReturn(Optional.of(creditNote));
        when(invoices.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(sourceInvoice));
        when(invoices.findLockedByIdAndCompanyId(20L, 1L)).thenReturn(Optional.of(targetInvoice));
        when(availableCredit.getAvailableAmount(creditNote, sourceInvoice, 1L))
                .thenReturn(new BigDecimal("20.00"));

        assertThrows(IllegalArgumentException.class, () -> service.allocate(
                5L, 20L, new BigDecimal("21.00"), date));
        verify(allocations, never()).save(any(CreditNoteAllocation.class));
    }

    @Test
    void rejectsAllocationAboveTheTargetInvoiceBalance() {
        when(creditNotes.findLockedByIdAndCompanyId(5L, 1L)).thenReturn(Optional.of(creditNote));
        when(invoices.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(sourceInvoice));
        when(invoices.findLockedByIdAndCompanyId(20L, 1L)).thenReturn(Optional.of(targetInvoice));
        when(availableCredit.getAvailableAmount(creditNote, sourceInvoice, 1L))
                .thenReturn(new BigDecimal("30.00"));
        when(invoiceService.getOutstandingAmount(targetInvoice)).thenReturn(new BigDecimal("20.00"));

        assertThrows(IllegalArgumentException.class, () -> service.allocate(
                5L, 20L, new BigDecimal("21.00"), date));
        verify(allocations, never()).save(any(CreditNoteAllocation.class));
    }

    @Test
    void rejectsAllocationsToAnInvoiceBelongingToAnotherCustomer() {
        Customer otherCustomer = new Customer();
        otherCustomer.setId(4L);
        targetInvoice.setCustomer(otherCustomer);
        when(creditNotes.findLockedByIdAndCompanyId(5L, 1L)).thenReturn(Optional.of(creditNote));
        when(invoices.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(sourceInvoice));
        when(invoices.findLockedByIdAndCompanyId(20L, 1L)).thenReturn(Optional.of(targetInvoice));

        assertThrows(IllegalArgumentException.class, () -> service.allocate(
                5L, 20L, new BigDecimal("10.00"), date));
        verify(allocations, never()).save(any(CreditNoteAllocation.class));
    }

    @Test
    void reversesAnAllocationAndReopensTheTargetInvoiceBalance() {
        CreditNoteAllocation allocation = new CreditNoteAllocation();
        allocation.setId(30L);
        allocation.setCreditNote(creditNote);
        allocation.setTargetInvoice(targetInvoice);
        allocation.setAmount(new BigDecimal("30.00"));
        allocation.setAllocationDate(date);
        targetInvoice.setStatus(InvoiceStatus.PAID);
        LocalDate reversalDate = date.plusDays(1);
        when(creditNotes.findLockedByIdAndCompanyId(5L, 1L)).thenReturn(Optional.of(creditNote));
        when(allocations.findLockedByIdAndCompanyId(30L, 1L)).thenReturn(Optional.of(allocation));
        when(invoices.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(sourceInvoice));
        when(invoices.findLockedByIdAndCompanyId(20L, 1L)).thenReturn(Optional.of(targetInvoice));
        when(allocations.save(allocation)).thenReturn(allocation);
        when(invoiceService.getOutstandingAmount(targetInvoice)).thenReturn(new BigDecimal("80.00"));
        when(creditNotes.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                1L, 20L, CreditNoteStatus.ISSUED)).thenReturn(BigDecimal.ZERO);
        when(allocations.sumByCompanyIdAndTargetInvoiceId(1L, 20L)).thenReturn(BigDecimal.ZERO);

        CreditNoteAllocation reversed = service.reverse(
                5L, 30L, reversalDate, "Applied to the wrong invoice");

        assertEquals(reversalDate, reversed.getReversalDate());
        assertEquals("Applied to the wrong invoice", reversed.getReversalReason());
        assertEquals(InvoiceStatus.SENT, targetInvoice.getStatus());
        verify(invoices).save(targetInvoice);
    }

    private Invoice invoice(Long id, String number, Customer customer, InvoiceStatus status,
                           String total, String paid) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setInvoiceNumber(number);
        invoice.setCustomer(customer);
        invoice.setStatus(status);
        invoice.setInvoiceDate(LocalDate.of(2026, 4, 15));
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setPaidAmount(new BigDecimal(paid));
        return invoice;
    }
}
