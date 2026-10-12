package com.cogitosum.service;

import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteRefund;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.Invoice;
import com.cogitosum.repository.ChartOfAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreditNoteRefundPostingServiceTest {

    @Test
    void postsRefundAsDebitToReceivablesAndCreditToBank() {
        GeneralJournalService journals = mock(GeneralJournalService.class);
        ChartOfAccountRepository accounts = mock(ChartOfAccountRepository.class);
        CurrentCompanyContext context = mock(CurrentCompanyContext.class);
        ChartOfAccount receivables = account(1L, "1100");
        when(context.requireCompanyId()).thenReturn(3L);
        when(accounts.findByCompanyIdAndAccountNumber(3L, "1100"))
                .thenReturn(Optional.of(receivables));
        when(journals.createJournal(any(GeneralJournal.class))).thenAnswer(invocation -> {
            GeneralJournal journal = invocation.getArgument(0);
            journal.setId(30L);
            return journal;
        });

        CreditNote note = new CreditNote();
        note.setId(8L);
        note.setCreditNoteNumber("CN-008");
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-008");
        note.setInvoice(invoice);
        CreditNoteRefund refund = new CreditNoteRefund();
        refund.setId(13L);
        refund.setCreditNote(note);
        refund.setRefundDate(LocalDate.of(2026, 5, 3));
        refund.setAmount(new BigDecimal("23.00"));
        refund.setBankAccount(account(2L, "1000"));

        new CreditNoteRefundPostingService(journals, accounts, context).postRefund(refund);

        ArgumentCaptor<GeneralJournal> captor = ArgumentCaptor.forClass(GeneralJournal.class);
        verify(journals).createJournal(captor.capture());
        GeneralJournal journal = captor.getValue();
        assertEquals("CREDIT-NOTE-REFUND-13", journal.getReference());
        assertEquals(new BigDecimal("23.00"), journal.getEntries().get(0).getDebit());
        assertEquals(new BigDecimal("23.00"), journal.getEntries().get(1).getCredit());
        assertEquals(receivables, journal.getEntries().get(0).getAccount());
        assertEquals("1000", journal.getEntries().get(1).getAccount().getAccountNumber());
        verify(journals).postJournal(30L, "auto-credit-note-refund");
    }

    private static ChartOfAccount account(Long id, String number) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setAccountNumber(number);
        return account;
    }
}
