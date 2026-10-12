package com.cogitosum.service;

import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteStatus;
import com.cogitosum.entity.Invoice;
import com.cogitosum.repository.CreditNoteAllocationRepository;
import com.cogitosum.repository.CreditNoteRefundRepository;
import com.cogitosum.repository.CreditNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CreditNoteAvailableCreditService {

    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteRefundRepository refundRepository;
    private final CreditNoteAllocationRepository allocationRepository;

    public CreditNoteAvailableCreditService(CreditNoteRepository creditNoteRepository,
                                            CreditNoteRefundRepository refundRepository,
                                            CreditNoteAllocationRepository allocationRepository) {
        this.creditNoteRepository = creditNoteRepository;
        this.refundRepository = refundRepository;
        this.allocationRepository = allocationRepository;
    }

    @Transactional(readOnly = true)
    public BigDecimal getAvailableAmount(CreditNote creditNote, Invoice sourceInvoice, Long companyId) {
        if (creditNote.getStatus() != CreditNoteStatus.ISSUED) {
            return BigDecimal.ZERO;
        }
        BigDecimal noteRemainder = zero(creditNote.getTotalAmount())
                .subtract(zero(refundRepository.sumByCompanyIdAndCreditNoteId(
                        companyId, creditNote.getId())))
                .subtract(zero(allocationRepository.sumByCompanyIdAndCreditNoteId(
                        companyId, creditNote.getId())));
        BigDecimal issuedCredits = zero(creditNoteRepository.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                companyId, sourceInvoice.getId(), CreditNoteStatus.ISSUED));
        BigDecimal refunds = zero(refundRepository.sumByCompanyIdAndInvoiceId(
                companyId, sourceInvoice.getId()));
        BigDecimal allocatedAway = zero(allocationRepository.sumByCompanyIdAndSourceInvoiceId(
                companyId, sourceInvoice.getId()));
        BigDecimal customerCredit = zero(sourceInvoice.getPaidAmount())
                .subtract(zero(sourceInvoice.getTotalAmount()))
                .add(issuedCredits)
                .subtract(refunds)
                .subtract(allocatedAway);
        return noteRemainder.min(customerCredit).max(BigDecimal.ZERO);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
