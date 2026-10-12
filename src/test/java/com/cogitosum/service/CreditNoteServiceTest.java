package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteStatus;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.repository.CreditNoteRepository;
import com.cogitosum.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditNoteServiceTest {

    @Mock private CreditNoteRepository creditNotes;
    @Mock private InvoiceRepository invoices;
    @Mock private CreditNotePostingService posting;
    @Mock private CurrentCompanyContext companyContext;

    private CreditNoteService service;

    @BeforeEach
    void setUp() {
        service = new CreditNoteService(creditNotes, invoices, posting, companyContext);
        when(companyContext.requireCompanyId()).thenReturn(1L);
    }

    @Test
    void createsDraftWithTaxesProportionalToTheSourceInvoice() {
        Invoice invoice = invoice(1L, "100.00", "5.00", "10.00", "115.00");
        when(companyContext.requireCompany()).thenReturn(new Company());
        when(invoices.findLockedByIdAndCompanyId(1L, 1L)).thenReturn(Optional.of(invoice));
        when(creditNotes.findTopByCompanyIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        when(creditNotes.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                1L, 1L, CreditNoteStatus.ISSUED)).thenReturn(BigDecimal.ZERO);
        when(creditNotes.save(any(CreditNote.class))).thenAnswer(invocation -> {
            CreditNote saved = invocation.getArgument(0);
            saved.setId(4L);
            return saved;
        });

        CreditNote note = service.createDraft(1L, "Returned portion", LocalDate.of(2026, 5, 1),
                List.of(new CreditNoteService.LineInput(
                        "Consulting", new BigDecimal("0.50"), new BigDecimal("40.00"))));

        assertEquals("CN-001", note.getCreditNoteNumber());
        assertEquals(CreditNoteStatus.DRAFT, note.getStatus());
        assertEquals(new BigDecimal("20.00"), note.getSubtotal());
        assertEquals(new BigDecimal("1.00"), note.getGstAmount());
        assertEquals(new BigDecimal("2.00"), note.getQstAmount());
        assertEquals(new BigDecimal("23.00"), note.getTotalAmount());
    }

    @Test
    void allowsCreditNotesForPaidInvoices() {
        Invoice invoice = invoice(1L, "100.00", "5.00", "10.00", "115.00");
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAmount(new BigDecimal("115.00"));
        when(companyContext.requireCompany()).thenReturn(new Company());
        when(invoices.findLockedByIdAndCompanyId(1L, 1L)).thenReturn(Optional.of(invoice));
        when(creditNotes.findTopByCompanyIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        when(creditNotes.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                1L, 1L, CreditNoteStatus.ISSUED)).thenReturn(BigDecimal.ZERO);
        when(creditNotes.save(any(CreditNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.canCredit(invoice));
        CreditNote note = service.createDraft(1L, "Refund for returned service",
                LocalDate.of(2026, 5, 1),
                List.of(new CreditNoteService.LineInput(
                        "Consulting", new BigDecimal("0.50"), new BigDecimal("40.00"))));

        assertEquals(CreditNoteStatus.DRAFT, note.getStatus());
        assertEquals(new BigDecimal("23.00"), note.getTotalAmount());
    }

    @Test
    void createsCreditNoteFromMultipleAuditableLines() {
        Invoice invoice = invoice(1L, "100.00", "5.00", "10.00", "115.00");
        when(companyContext.requireCompany()).thenReturn(new Company());
        when(invoices.findLockedByIdAndCompanyId(1L, 1L)).thenReturn(Optional.of(invoice));
        when(creditNotes.findTopByCompanyIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        when(creditNotes.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                1L, 1L, CreditNoteStatus.ISSUED)).thenReturn(BigDecimal.ZERO);
        when(creditNotes.save(any(CreditNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreditNote note = service.createDraft(1L, "Split return", LocalDate.of(2026, 5, 1),
                List.of(
                        new CreditNoteService.LineInput(
                                "Consulting", new BigDecimal("0.50"), new BigDecimal("40.00")),
                        new CreditNoteService.LineInput(
                                "Travel", BigDecimal.ONE, new BigDecimal("10.00"))));

        assertEquals(2, note.getLineItems().size());
        assertEquals(new BigDecimal("30.00"), note.getSubtotal());
        assertEquals(new BigDecimal("1.50"), note.getGstAmount());
        assertEquals(new BigDecimal("3.00"), note.getQstAmount());
        assertEquals(new BigDecimal("34.50"), note.getTotalAmount());
    }

    @Test
    void rejectsCreditsThatExceedTheRemainingInvoiceValue() {
        Invoice invoice = invoice(1L, "100.00", "5.00", "10.00", "115.00");
        when(invoices.findLockedByIdAndCompanyId(1L, 1L)).thenReturn(Optional.of(invoice));
        when(creditNotes.findTopByCompanyIdOrderByIdDesc(1L)).thenReturn(Optional.empty());
        when(creditNotes.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                1L, 1L, CreditNoteStatus.ISSUED)).thenReturn(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class, () -> service.createDraft(
                1L, "Exceeds available credit", LocalDate.now(),
                List.of(new CreditNoteService.LineInput(
                        "Consulting", BigDecimal.ONE, new BigDecimal("20.00")))));
        verify(creditNotes, never()).save(any(CreditNote.class));
    }

    @Test
    void issuingFullCreditPostsReversalAndClosesInvoiceAsCredited() {
        Invoice invoice = invoice(1L, "100.00", "5.00", "10.00", "115.00");
        CreditNote note = new CreditNote();
        note.setId(9L);
        note.setInvoice(invoice);
        note.setCreditNoteNumber("CN-009");
        note.setCreditNoteDate(LocalDate.of(2026, 5, 1));
        note.setStatus(CreditNoteStatus.DRAFT);
        note.setSubtotal(new BigDecimal("100.00"));
        note.setGstAmount(new BigDecimal("5.00"));
        note.setHstAmount(BigDecimal.ZERO);
        note.setQstAmount(new BigDecimal("10.00"));
        note.setTotalAmount(new BigDecimal("115.00"));
        when(creditNotes.findLockedByIdAndCompanyId(9L, 1L)).thenReturn(Optional.of(note));
        when(invoices.findLockedByIdAndCompanyId(1L, 1L)).thenReturn(Optional.of(invoice));
        when(creditNotes.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                1L, 1L, CreditNoteStatus.ISSUED)).thenReturn(BigDecimal.ZERO, new BigDecimal("115.00"));
        when(creditNotes.save(note)).thenReturn(note);
        when(invoices.save(invoice)).thenReturn(invoice);

        CreditNote issued = service.issue(9L);

        assertEquals(CreditNoteStatus.ISSUED, issued.getStatus());
        assertEquals(InvoiceStatus.CREDITED, invoice.getStatus());
        verify(posting).postCreditNote(note);
    }

    private Invoice invoice(Long id, String subtotal, String gst, String qst, String total) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setSubtotal(new BigDecimal(subtotal));
        invoice.setGstAmount(new BigDecimal(gst));
        invoice.setHstAmount(BigDecimal.ZERO);
        invoice.setQstAmount(new BigDecimal(qst));
        invoice.setTotalAmount(new BigDecimal(total));
        return invoice;
    }
}
