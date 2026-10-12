package com.cogitosum.service;

import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteAllocation;
import com.cogitosum.entity.CreditNoteStatus;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.repository.CreditNoteAllocationRepository;
import com.cogitosum.repository.CreditNoteRepository;
import com.cogitosum.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CreditNoteAllocationService {

    private final CreditNoteAllocationRepository allocationRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final CreditNoteAvailableCreditService availableCreditService;
    private final CurrentCompanyContext companyContext;

    public CreditNoteAllocationService(CreditNoteAllocationRepository allocationRepository,
                                       CreditNoteRepository creditNoteRepository,
                                       InvoiceRepository invoiceRepository,
                                       InvoiceService invoiceService,
                                       CreditNoteAvailableCreditService availableCreditService,
                                       CurrentCompanyContext companyContext) {
        this.allocationRepository = allocationRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.availableCreditService = availableCreditService;
        this.companyContext = companyContext;
    }

    @Transactional(readOnly = true)
    public List<CreditNoteAllocation> getAllocations(Long creditNoteId) {
        Long companyId = companyContext.requireCompanyId();
        requireCreditNote(creditNoteId, companyId);
        return allocationRepository.findByCompanyIdAndCreditNoteIdOrderByAllocationDateDescIdDesc(
                companyId, creditNoteId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getAvailableAmount(Long creditNoteId) {
        Long companyId = companyContext.requireCompanyId();
        CreditNote creditNote = requireCreditNote(creditNoteId, companyId);
        return availableCreditService.getAvailableAmount(
                creditNote, creditNote.getInvoice(), companyId);
    }

    @Transactional(readOnly = true)
    public List<AllocationTarget> getEligibleTargets(Long creditNoteId) {
        Long companyId = companyContext.requireCompanyId();
        CreditNote creditNote = requireCreditNote(creditNoteId, companyId);
        if (creditNote.getStatus() != CreditNoteStatus.ISSUED
                || getAvailableAmount(creditNoteId).signum() <= 0) {
            return List.of();
        }
        return invoiceService.getInvoicesByCustomerId(
                        creditNote.getInvoice().getCustomer().getId()).stream()
                .filter(invoice -> !Objects.equals(invoice.getId(), creditNote.getInvoice().getId()))
                .filter(this::canReceiveAllocation)
                .map(invoice -> new AllocationTarget(invoice, invoiceService.getOutstandingAmount(invoice)))
                .filter(target -> target.outstandingAmount().signum() > 0)
                .toList();
    }

    @Transactional
    public CreditNoteAllocation allocate(Long creditNoteId, Long targetInvoiceId,
                                         BigDecimal amount, LocalDate allocationDate) {
        Long companyId = companyContext.requireCompanyId();
        CreditNote creditNote = creditNoteRepository.findLockedByIdAndCompanyId(creditNoteId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit note not found"));
        Long sourceInvoiceId = creditNote.getInvoice().getId();
        if (targetInvoiceId == null || Objects.equals(sourceInvoiceId, targetInvoiceId)) {
            throw new IllegalArgumentException("Select a different invoice for this credit allocation");
        }
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Allocation amount must be positive and use at most two decimals");
        }
        if (allocationDate == null || allocationDate.isBefore(creditNote.getCreditNoteDate())) {
            throw new IllegalArgumentException("Allocation date cannot be before the credit note date");
        }
        if (creditNote.getStatus() != CreditNoteStatus.ISSUED) {
            throw new IllegalStateException("Only issued credit notes can be allocated");
        }

        Map<Long, Invoice> lockedInvoices = lockInvoices(sourceInvoiceId, targetInvoiceId, companyId);
        Invoice sourceInvoice = lockedInvoices.get(sourceInvoiceId);
        Invoice targetInvoice = lockedInvoices.get(targetInvoiceId);
        if (!Objects.equals(sourceInvoice.getCustomer().getId(), targetInvoice.getCustomer().getId())) {
            throw new IllegalArgumentException("Credit can only be allocated to another invoice for the same customer");
        }
        if (!canReceiveAllocation(targetInvoice)) {
            throw new IllegalArgumentException("Select an issued, active invoice with an outstanding balance");
        }
        if (targetInvoice.getInvoiceDate() != null && allocationDate.isBefore(targetInvoice.getInvoiceDate())) {
            throw new IllegalArgumentException("Allocation date cannot be before the target invoice date");
        }

        BigDecimal availableCredit = availableCreditService.getAvailableAmount(
                creditNote, sourceInvoice, companyId);
        if (amount.compareTo(availableCredit) > 0) {
            throw new IllegalArgumentException("Allocation exceeds the customer's available credit");
        }
        BigDecimal targetOutstanding = invoiceService.getOutstandingAmount(targetInvoice);
        if (amount.compareTo(targetOutstanding) > 0) {
            throw new IllegalArgumentException("Allocation exceeds the target invoice balance");
        }

        CreditNoteAllocation allocation = new CreditNoteAllocation();
        allocation.setCompany(companyContext.requireCompany());
        allocation.setCreditNote(creditNote);
        allocation.setTargetInvoice(targetInvoice);
        allocation.setAmount(amount);
        allocation.setAllocationDate(allocationDate);
        CreditNoteAllocation saved = allocationRepository.save(allocation);

        BigDecimal remaining = targetOutstanding.subtract(amount);
        if (remaining.signum() <= 0) {
            targetInvoice.setStatus(InvoiceStatus.PAID);
        } else {
            targetInvoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        invoiceRepository.save(targetInvoice);
        return saved;
    }

    @Transactional
    public CreditNoteAllocation reverse(Long creditNoteId, Long allocationId,
                                        LocalDate reversalDate, String reversalReason) {
        Long companyId = companyContext.requireCompanyId();
        CreditNote creditNote = creditNoteRepository.findLockedByIdAndCompanyId(creditNoteId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit note not found"));
        if (creditNote.getStatus() != CreditNoteStatus.ISSUED) {
            throw new IllegalStateException("Only issued credit notes can have allocations reversed");
        }
        CreditNoteAllocation allocation = allocationRepository.findLockedByIdAndCompanyId(allocationId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit allocation not found"));
        if (!Objects.equals(allocation.getCreditNote().getId(), creditNote.getId())) {
            throw new IllegalArgumentException("Credit allocation does not belong to this credit note");
        }
        if (allocation.getReversalDate() != null) {
            throw new IllegalStateException("This credit allocation has already been reversed");
        }
        if (reversalDate == null || reversalDate.isBefore(allocation.getAllocationDate())) {
            throw new IllegalArgumentException("Reversal date cannot be before the allocation date");
        }
        if (reversalReason == null || reversalReason.isBlank() || reversalReason.trim().length() > 1000) {
            throw new IllegalArgumentException("A reversal reason of up to 1000 characters is required");
        }

        Long sourceInvoiceId = creditNote.getInvoice().getId();
        Long targetInvoiceId = allocation.getTargetInvoice().getId();
        Map<Long, Invoice> lockedInvoices = lockInvoices(sourceInvoiceId, targetInvoiceId, companyId);
        Invoice targetInvoice = lockedInvoices.get(targetInvoiceId);
        allocation.setReversalDate(reversalDate);
        allocation.setReversalReason(reversalReason.trim());
        CreditNoteAllocation reversed = allocationRepository.save(allocation);

        if (targetInvoice.getStatus() != InvoiceStatus.CANCELLED
                && targetInvoice.getStatus() != InvoiceStatus.DRAFT) {
            BigDecimal outstanding = invoiceService.getOutstandingAmount(targetInvoice);
            BigDecimal credits = creditNoteRepository.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                    companyId, targetInvoiceId, CreditNoteStatus.ISSUED);
            if (outstanding.signum() <= 0) {
                targetInvoice.setStatus(credits != null && credits.signum() > 0
                        ? InvoiceStatus.CREDITED : InvoiceStatus.PAID);
            } else if (zero(targetInvoice.getPaidAmount()).signum() > 0
                    || zero(allocationRepository.sumByCompanyIdAndTargetInvoiceId(
                            companyId, targetInvoiceId)).signum() > 0) {
                targetInvoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
            } else {
                targetInvoice.setStatus(targetInvoice.getDueDate() != null
                        && targetInvoice.getDueDate().isBefore(LocalDate.now())
                        ? InvoiceStatus.OVERDUE : InvoiceStatus.SENT);
            }
            invoiceRepository.save(targetInvoice);
        }
        return reversed;
    }

    private Map<Long, Invoice> lockInvoices(Long sourceInvoiceId, Long targetInvoiceId, Long companyId) {
        Map<Long, Invoice> invoices = new HashMap<>();
        List.of(sourceInvoiceId, targetInvoiceId).stream().sorted(Comparator.naturalOrder()).forEach(id -> {
            Invoice invoice = invoiceRepository.findLockedByIdAndCompanyId(id, companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
            invoices.put(id, invoice);
        });
        return invoices;
    }

    private boolean canReceiveAllocation(Invoice invoice) {
        InvoiceStatus status = invoice.getStatus();
        return status != null && status != InvoiceStatus.DRAFT
                && status != InvoiceStatus.CANCELLED && status != InvoiceStatus.REFUNDED;
    }

    private CreditNote requireCreditNote(Long creditNoteId, Long companyId) {
        return creditNoteRepository.findByIdAndCompanyId(creditNoteId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit note not found"));
    }

    private BigDecimal zero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public record AllocationTarget(Invoice invoice, BigDecimal outstandingAmount) {
    }
}
