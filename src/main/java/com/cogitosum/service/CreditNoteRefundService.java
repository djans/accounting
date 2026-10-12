package com.cogitosum.service;

import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteRefund;
import com.cogitosum.entity.CreditNoteStatus;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.PaymentMethod;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.CreditNoteRefundRepository;
import com.cogitosum.repository.CreditNoteRepository;
import com.cogitosum.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class CreditNoteRefundService {

    private final CreditNoteRefundRepository refundRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final InvoiceRepository invoiceRepository;
    private final ChartOfAccountRepository accountRepository;
    private final CreditNoteRefundPostingService postingService;
    private final CreditNoteAvailableCreditService availableCreditService;
    private final CurrentCompanyContext companyContext;

    public CreditNoteRefundService(CreditNoteRefundRepository refundRepository,
                                   CreditNoteRepository creditNoteRepository,
                                   InvoiceRepository invoiceRepository,
                                   ChartOfAccountRepository accountRepository,
                                   CreditNoteRefundPostingService postingService,
                                   CreditNoteAvailableCreditService availableCreditService,
                                   CurrentCompanyContext companyContext) {
        this.refundRepository = refundRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.invoiceRepository = invoiceRepository;
        this.accountRepository = accountRepository;
        this.postingService = postingService;
        this.availableCreditService = availableCreditService;
        this.companyContext = companyContext;
    }

    @Transactional(readOnly = true)
    public List<CreditNoteRefund> getRefunds(Long creditNoteId) {
        Long companyId = companyContext.requireCompanyId();
        requireCreditNote(creditNoteId, companyId);
        return refundRepository.findByCompanyIdAndCreditNoteIdOrderByRefundDateDescIdDesc(
                companyId, creditNoteId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getRefundableAmount(Long creditNoteId) {
        Long companyId = companyContext.requireCompanyId();
        CreditNote creditNote = requireCreditNote(creditNoteId, companyId);
        return refundableAmount(creditNote, creditNote.getInvoice(), companyId);
    }

    @Transactional
    public CreditNoteRefund refund(Long creditNoteId, BigDecimal amount, LocalDate refundDate,
                                  Long bankAccountId, PaymentMethod paymentMethod, String transactionId,
                                  String reason) {
        Long companyId = companyContext.requireCompanyId();
        CreditNote creditNote = creditNoteRepository.findLockedByIdAndCompanyId(creditNoteId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit note not found"));
        Invoice invoice = invoiceRepository.findLockedByIdAndCompanyId(
                        creditNote.getInvoice().getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        if (creditNote.getStatus() != CreditNoteStatus.ISSUED) {
            throw new IllegalStateException("Only issued credit notes can be refunded");
        }
        if (amount == null || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Refund amount must be positive and use at most two decimals");
        }
        if (refundDate == null || refundDate.isBefore(creditNote.getCreditNoteDate())) {
            throw new IllegalArgumentException("Refund date cannot be before the credit note date");
        }
        if (paymentMethod == null) {
            throw new IllegalArgumentException("Refund method is required");
        }
        if (reason == null || reason.isBlank() || reason.trim().length() > 1000) {
            throw new IllegalArgumentException("A refund reason of up to 1000 characters is required");
        }
        ChartOfAccount bankAccount = accountRepository.findByIdAndCompanyId(bankAccountId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));
        if (bankAccount.getCategory() != AccountCategory.BANK || !Boolean.TRUE.equals(bankAccount.getActive())) {
            throw new IllegalArgumentException("Select an active bank account");
        }
        String reference = transactionId == null || transactionId.isBlank()
                ? null : transactionId.trim();
        if (reference != null && reference.length() > 255) {
            throw new IllegalArgumentException("Refund reference cannot exceed 255 characters");
        }
        if (reference != null && refundRepository.existsByCompanyIdAndTransactionId(companyId, reference)) {
            throw new IllegalArgumentException("Refund reference already exists");
        }

        BigDecimal refundableAmount = refundableAmount(creditNote, invoice, companyId);
        if (amount.compareTo(refundableAmount) > 0) {
            throw new IllegalArgumentException("Refund exceeds the customer's available credit");
        }

        CreditNoteRefund refund = new CreditNoteRefund();
        refund.setCompany(companyContext.requireCompany());
        refund.setCreditNote(creditNote);
        refund.setBankAccount(bankAccount);
        refund.setAmount(amount);
        refund.setRefundDate(refundDate);
        refund.setPaymentMethod(paymentMethod);
        refund.setTransactionId(reference);
        refund.setReason(reason.trim());
        CreditNoteRefund saved = refundRepository.save(refund);
        postingService.postRefund(saved);
        return saved;
    }

    private CreditNote requireCreditNote(Long creditNoteId, Long companyId) {
        return creditNoteRepository.findByIdAndCompanyId(creditNoteId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit note not found"));
    }

    private BigDecimal refundableAmount(CreditNote creditNote, Invoice invoice, Long companyId) {
        return availableCreditService.getAvailableAmount(creditNote, invoice, companyId);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
