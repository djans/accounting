package com.cogitosum.service;

import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.Company;
import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteRefund;
import com.cogitosum.entity.CreditNoteStatus;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.PaymentMethod;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.CreditNoteRefundRepository;
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
class CreditNoteRefundServiceTest {

    @Mock private CreditNoteRefundRepository refunds;
    @Mock private CreditNoteRepository creditNotes;
    @Mock private InvoiceRepository invoices;
    @Mock private ChartOfAccountRepository accounts;
    @Mock private CreditNoteRefundPostingService posting;
    @Mock private CreditNoteAvailableCreditService availableCredit;
    @Mock private CurrentCompanyContext companyContext;

    private CreditNoteRefundService service;
    private CreditNote creditNote;
    private Invoice invoice;
    private ChartOfAccount bankAccount;

    @BeforeEach
    void setUp() {
        service = new CreditNoteRefundService(
                refunds, creditNotes, invoices, accounts, posting, availableCredit, companyContext);
        when(companyContext.requireCompanyId()).thenReturn(1L);
        invoice = new Invoice();
        invoice.setId(4L);
        invoice.setTotalAmount(new BigDecimal("105.00"));
        invoice.setPaidAmount(new BigDecimal("105.00"));
        creditNote = new CreditNote();
        creditNote.setId(9L);
        creditNote.setStatus(CreditNoteStatus.ISSUED);
        creditNote.setCreditNoteDate(LocalDate.of(2026, 5, 1));
        creditNote.setTotalAmount(new BigDecimal("23.00"));
        creditNote.setInvoice(invoice);
        bankAccount = new ChartOfAccount();
        bankAccount.setId(12L);
        bankAccount.setCategory(AccountCategory.BANK);
        bankAccount.setActive(true);
    }

    @Test
    void refundsPaidInvoiceCreditAndPostsOnlyTheAmountActuallyOwedToCustomer() {
        when(creditNotes.findLockedByIdAndCompanyId(9L, 1L)).thenReturn(Optional.of(creditNote));
        when(invoices.findLockedByIdAndCompanyId(4L, 1L)).thenReturn(Optional.of(invoice));
        when(availableCredit.getAvailableAmount(creditNote, invoice, 1L))
                .thenReturn(new BigDecimal("23.00"));
        when(accounts.findByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(bankAccount));
        when(companyContext.requireCompany()).thenReturn(new Company());
        when(refunds.save(any(CreditNoteRefund.class))).thenAnswer(invocation -> {
            CreditNoteRefund saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        CreditNoteRefund refund = service.refund(9L, new BigDecimal("23.00"),
                LocalDate.of(2026, 5, 2), 12L, PaymentMethod.BANK_TRANSFER, "REF-20",
                "Refund after credit note");

        assertEquals(new BigDecimal("23.00"), refund.getAmount());
        assertEquals(PaymentMethod.BANK_TRANSFER, refund.getPaymentMethod());
        verify(posting).postRefund(refund);
    }

    @Test
    void doesNotAllowRefundingMoreThanTheCustomerCreditBalance() {
        when(creditNotes.findLockedByIdAndCompanyId(9L, 1L)).thenReturn(Optional.of(creditNote));
        when(invoices.findLockedByIdAndCompanyId(4L, 1L)).thenReturn(Optional.of(invoice));
        when(availableCredit.getAvailableAmount(creditNote, invoice, 1L))
                .thenReturn(new BigDecimal("23.00"));
        when(accounts.findByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(bankAccount));

        assertThrows(IllegalArgumentException.class, () -> service.refund(
                9L, new BigDecimal("24.00"), LocalDate.of(2026, 5, 2), 12L,
                PaymentMethod.BANK_TRANSFER, null, "Refund after credit note"));
        verify(refunds, never()).save(any(CreditNoteRefund.class));
        verify(posting, never()).postRefund(any(CreditNoteRefund.class));
    }

    @Test
    void doesNotRefundCreditWhichOnlyReducesAnUnpaidInvoiceBalance() {
        invoice.setPaidAmount(BigDecimal.ZERO);
        when(creditNotes.findByIdAndCompanyId(9L, 1L)).thenReturn(Optional.of(creditNote));
        when(availableCredit.getAvailableAmount(creditNote, invoice, 1L))
                .thenReturn(BigDecimal.ZERO);

        assertEquals(BigDecimal.ZERO, service.getRefundableAmount(9L));
    }
}
