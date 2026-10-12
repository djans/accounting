package com.cogitosum.service;

import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteStatus;
import com.cogitosum.entity.Invoice;
import com.cogitosum.repository.CreditNoteAllocationRepository;
import com.cogitosum.repository.CreditNoteRefundRepository;
import com.cogitosum.repository.CreditNoteRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreditNoteAvailableCreditServiceTest {

    @Test
    void reservesAllocationsAndRefundsAgainstTheSameAvailableCredit() {
        CreditNoteRepository creditNotes = mock(CreditNoteRepository.class);
        CreditNoteRefundRepository refunds = mock(CreditNoteRefundRepository.class);
        CreditNoteAllocationRepository allocations = mock(CreditNoteAllocationRepository.class);
        CreditNoteAvailableCreditService service = new CreditNoteAvailableCreditService(
                creditNotes, refunds, allocations);
        Invoice invoice = new Invoice();
        invoice.setId(4L);
        invoice.setTotalAmount(new BigDecimal("1000.00"));
        invoice.setPaidAmount(new BigDecimal("1050.00"));
        CreditNote note = new CreditNote();
        note.setId(8L);
        note.setStatus(CreditNoteStatus.ISSUED);
        note.setTotalAmount(new BigDecimal("100.00"));
        when(creditNotes.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                1L, 4L, CreditNoteStatus.ISSUED)).thenReturn(new BigDecimal("100.00"));
        when(refunds.sumByCompanyIdAndCreditNoteId(1L, 8L)).thenReturn(new BigDecimal("10.00"));
        when(refunds.sumByCompanyIdAndInvoiceId(1L, 4L)).thenReturn(new BigDecimal("10.00"));
        when(allocations.sumByCompanyIdAndCreditNoteId(1L, 8L)).thenReturn(new BigDecimal("20.00"));
        when(allocations.sumByCompanyIdAndSourceInvoiceId(1L, 4L)).thenReturn(new BigDecimal("20.00"));

        assertEquals(new BigDecimal("70.00"), service.getAvailableAmount(note, invoice, 1L));
    }

    @Test
    void doesNotMakeCreditOnAnUnpaidInvoiceAvailableForRefundOrTransfer() {
        CreditNoteRepository creditNotes = mock(CreditNoteRepository.class);
        CreditNoteRefundRepository refunds = mock(CreditNoteRefundRepository.class);
        CreditNoteAllocationRepository allocations = mock(CreditNoteAllocationRepository.class);
        CreditNoteAvailableCreditService service = new CreditNoteAvailableCreditService(
                creditNotes, refunds, allocations);
        Invoice invoice = new Invoice();
        invoice.setId(4L);
        invoice.setTotalAmount(new BigDecimal("100.00"));
        invoice.setPaidAmount(BigDecimal.ZERO);
        CreditNote note = new CreditNote();
        note.setId(8L);
        note.setStatus(CreditNoteStatus.ISSUED);
        note.setTotalAmount(new BigDecimal("25.00"));
        when(creditNotes.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                1L, 4L, CreditNoteStatus.ISSUED)).thenReturn(new BigDecimal("25.00"));

        assertEquals(BigDecimal.ZERO, service.getAvailableAmount(note, invoice, 1L));
    }
}
