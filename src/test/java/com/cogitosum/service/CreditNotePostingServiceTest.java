package com.cogitosum.service;

import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.Invoice;
import com.cogitosum.repository.ChartOfAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreditNotePostingServiceTest {

    @Test
    void postsTheCreditAsDebitsToRevenueAndTaxesAndACreditToReceivables() {
        GeneralJournalService journals = mock(GeneralJournalService.class);
        ChartOfAccountRepository accounts = mock(ChartOfAccountRepository.class);
        CurrentCompanyContext context = mock(CurrentCompanyContext.class);
        when(context.requireCompanyId()).thenReturn(1L);
        Map<String, ChartOfAccount> accountByNumber = Map.of(
                "1100", account(1L, "1100"),
                "2310", account(2L, "2310"),
                "2320", account(3L, "2320"),
                "4000", account(4L, "4000"));
        when(accounts.findByCompanyIdAndAccountNumber(1L, "1100"))
                .thenReturn(Optional.of(accountByNumber.get("1100")));
        when(accounts.findByCompanyIdAndAccountNumber(1L, "2310"))
                .thenReturn(Optional.of(accountByNumber.get("2310")));
        when(accounts.findByCompanyIdAndAccountNumber(1L, "2320"))
                .thenReturn(Optional.of(accountByNumber.get("2320")));
        when(accounts.findByCompanyIdAndAccountNumber(1L, "4000"))
                .thenReturn(Optional.of(accountByNumber.get("4000")));
        when(journals.createJournal(any(GeneralJournal.class))).thenAnswer(invocation -> {
            GeneralJournal journal = invocation.getArgument(0);
            journal.setId(8L);
            return journal;
        });

        CreditNote note = new CreditNote();
        note.setId(12L);
        note.setCreditNoteNumber("CN-012");
        note.setCreditNoteDate(LocalDate.of(2026, 5, 1));
        note.setSubtotal(new BigDecimal("100.00"));
        note.setGstAmount(new BigDecimal("5.00"));
        note.setHstAmount(BigDecimal.ZERO);
        note.setQstAmount(new BigDecimal("9.98"));
        note.setTotalAmount(new BigDecimal("114.98"));
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-12");
        note.setInvoice(invoice);

        new CreditNotePostingService(journals, accounts, context).postCreditNote(note);

        ArgumentCaptor<GeneralJournal> journalCaptor = ArgumentCaptor.forClass(GeneralJournal.class);
        verify(journals).createJournal(journalCaptor.capture());
        GeneralJournal postedJournal = journalCaptor.getValue();
        assertEquals("CREDIT-NOTE-12", postedJournal.getReference());
        assertEquals(new BigDecimal("114.98"), postedJournal.getEntries().stream()
                .map(entry -> entry.getDebit() == null ? BigDecimal.ZERO : entry.getDebit())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(new BigDecimal("114.98"), postedJournal.getEntries().stream()
                .map(entry -> entry.getCredit() == null ? BigDecimal.ZERO : entry.getCredit())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        verify(journals).postJournal(8L, "auto-credit-note");
    }

    private static ChartOfAccount account(Long id, String number) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setAccountNumber(number);
        return account;
    }
}
