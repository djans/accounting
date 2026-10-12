package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.CreditNoteStatus;
import com.cogitosum.entity.Payment;
import com.cogitosum.entity.PaymentStatus;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.PaymentRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.CreditNoteRepository;
import com.cogitosum.repository.CreditNoteRefundRepository;
import com.cogitosum.repository.CreditNoteAllocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentPostingService paymentPostingService;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private CurrentCompanyContext companyContext;

    @Autowired
    private CreditNoteRepository creditNoteRepository;

    @Autowired
    private CreditNoteRefundRepository creditNoteRefundRepository;

    @Autowired
    private CreditNoteAllocationRepository creditNoteAllocationRepository;

    @Transactional
    public Payment recordPayment(Payment payment) {
        Long companyId = companyContext.requireCompanyId();
        payment.setCompany(companyContext.requireCompany());
        Invoice invoice = invoiceRepository.findLockedByIdAndCompanyId(
                        requiredId(payment.getInvoice(), "Invoice"), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        if (invoice.getStatus() == InvoiceStatus.DRAFT
                || invoice.getStatus() == InvoiceStatus.CANCELLED
                || invoice.getStatus() == InvoiceStatus.PAID
                || invoice.getStatus() == InvoiceStatus.CREDITED
                || invoice.getStatus() == InvoiceStatus.REFUNDED) {
            throw new IllegalStateException("Only issued, active invoices can receive payments");
        }
        if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        BigDecimal remaining = outstandingAmount(invoice, companyId);
        if (payment.getAmount().compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Payment amount exceeds the invoice's outstanding balance");
        }
        payment.setInvoice(invoice);
        payment.setBankAccount(resolveAccount(payment.getBankAccount(), companyId, "Bank account"));
        payment.setArAccount(resolveAccount(payment.getArAccount(), companyId, "A/R account"));
        if (payment.getTransactionId() == null || payment.getTransactionId().isEmpty()) {
            payment.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (payment.getPaymentDate() == null) {
            payment.setPaymentDate(LocalDate.now());
        }

        Payment savedPayment = paymentRepository.save(payment);

        // Post Dr Bank / Cr A/R if a bank account was provided
        paymentPostingService.postPayment(savedPayment);

        recalculateInvoicePaymentState(payment.getInvoice().getId());

        return savedPayment;
    }

    public Optional<Payment> getPaymentById(Long id) {
        return paymentRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public Optional<Payment> getPaymentByTransactionId(String transactionId) {
        return paymentRepository.findByCompanyIdAndTransactionId(companyContext.requireCompanyId(), transactionId);
    }

    public List<Payment> getPaymentsByInvoiceId(Long invoiceId) {
        return paymentRepository.findByCompanyIdAndInvoiceId(companyContext.requireCompanyId(), invoiceId);
    }

    public List<Payment> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByCompanyIdAndStatus(companyContext.requireCompanyId(), status);
    }

    public List<Payment> getPaymentsByDateRange(LocalDate startDate, LocalDate endDate) {
        return paymentRepository.findByCompanyIdAndPaymentDateBetween(companyContext.requireCompanyId(), startDate, endDate);
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAllByCompanyId(companyContext.requireCompanyId());
    }

    public java.util.List<Payment> getPaymentsByMethod(com.cogitosum.entity.PaymentMethod paymentMethod) {
        return paymentRepository.findByCompanyIdAndPaymentMethod(companyContext.requireCompanyId(), paymentMethod);
    }

    @Transactional
    public Payment markPaymentAsCompleted(Long id) {
        Payment payment = paymentRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        if (payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new IllegalStateException("Only active payments can be marked as completed");
        }
        payment.setStatus(PaymentStatus.COMPLETED);
        Payment savedPayment = paymentRepository.save(payment);
        recalculateInvoicePaymentState(payment.getInvoice().getId());
        return savedPayment;
    }

    @Transactional
    public Payment refundPayment(Long id) {
        return refundPayment(id, "Payment refunded");
    }

    @Transactional
    public Payment refundPayment(Long id, String reason) {
        Payment payment = paymentRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new IllegalStateException("Payment has already been reversed");
        }
        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Only active payments can be reversed");
        }

        paymentPostingService.reversePayment(payment, reason);
        payment.setStatus(PaymentStatus.REFUNDED);
        Payment savedPayment = paymentRepository.save(payment);
        recalculateInvoicePaymentState(payment.getInvoice().getId());
        return savedPayment;
    }

    @Transactional
    public void deletePayment(Long id) {
        Payment payment = paymentRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        Long invoiceId = payment.getInvoice().getId();
        paymentPostingService.reversePayment(payment, "Payment deleted");
        paymentRepository.delete(payment);
        recalculateInvoicePaymentState(invoiceId);
    }

    private void recalculateInvoicePaymentState(Long invoiceId) {
        Long companyId = companyContext.requireCompanyId();
        Invoice invoice = invoiceRepository.findLockedByIdAndCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new IllegalStateException("Invoice not found for payment"));
        BigDecimal openingPaidAmount = invoice.getOpeningPaidAmount() == null
                ? BigDecimal.ZERO : invoice.getOpeningPaidAmount();
        BigDecimal paidAmount = paymentRepository.findByCompanyIdAndInvoiceId(companyId, invoiceId).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING
                        || payment.getStatus() == PaymentStatus.COMPLETED)
                .map(Payment::getAmount)
                .reduce(openingPaidAmount, BigDecimal::add);

        invoice.setPaidAmount(paidAmount);
        BigDecimal creditedAmount = creditNoteRepository.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                companyId, invoiceId, CreditNoteStatus.ISSUED);
        creditedAmount = creditedAmount == null ? BigDecimal.ZERO : creditedAmount;
        BigDecimal refundedAmount = creditNoteRefundRepository.sumByCompanyIdAndInvoiceId(
                companyId, invoiceId);
        refundedAmount = refundedAmount == null ? BigDecimal.ZERO : refundedAmount;
        BigDecimal allocatedFromInvoice = creditNoteAllocationRepository
                .sumByCompanyIdAndSourceInvoiceId(companyId, invoiceId);
        allocatedFromInvoice = allocatedFromInvoice == null ? BigDecimal.ZERO : allocatedFromInvoice;
        BigDecimal allocatedToInvoice = creditNoteAllocationRepository
                .sumByCompanyIdAndTargetInvoiceId(companyId, invoiceId);
        allocatedToInvoice = allocatedToInvoice == null ? BigDecimal.ZERO : allocatedToInvoice;
        if (invoice.getStatus() != InvoiceStatus.CANCELLED && invoice.getStatus() != InvoiceStatus.DRAFT) {
            BigDecimal balance = invoice.getTotalAmount().subtract(paidAmount)
                    .subtract(creditedAmount).add(refundedAmount)
                    .add(allocatedFromInvoice).subtract(allocatedToInvoice);
            if (balance.signum() <= 0) {
                invoice.setStatus(creditedAmount.signum() > 0 ? InvoiceStatus.CREDITED : InvoiceStatus.PAID);
            } else if (paidAmount.compareTo(BigDecimal.ZERO) > 0 || allocatedToInvoice.signum() > 0) {
                invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
            } else if (invoice.getStatus() == InvoiceStatus.PAID
                    || invoice.getStatus() == InvoiceStatus.PARTIALLY_PAID
                    || invoice.getStatus() == InvoiceStatus.CREDITED
                    || invoice.getStatus() == InvoiceStatus.REFUNDED) {
                invoice.setStatus(invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now())
                        ? InvoiceStatus.OVERDUE : InvoiceStatus.SENT);
            }
        }
        invoiceRepository.save(invoice);
    }

    private BigDecimal outstandingAmount(Invoice invoice, Long companyId) {
        BigDecimal paidAmount = invoice.getPaidAmount() == null ? BigDecimal.ZERO : invoice.getPaidAmount();
        BigDecimal creditedAmount = creditNoteRepository.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                companyId, invoice.getId(), CreditNoteStatus.ISSUED);
        BigDecimal refundedAmount = creditNoteRefundRepository.sumByCompanyIdAndInvoiceId(
                companyId, invoice.getId());
        BigDecimal allocatedFromInvoice = creditNoteAllocationRepository
                .sumByCompanyIdAndSourceInvoiceId(companyId, invoice.getId());
        BigDecimal allocatedToInvoice = creditNoteAllocationRepository
                .sumByCompanyIdAndTargetInvoiceId(companyId, invoice.getId());
        return invoice.getTotalAmount().subtract(paidAmount)
                .subtract(creditedAmount == null ? BigDecimal.ZERO : creditedAmount)
                .add(refundedAmount == null ? BigDecimal.ZERO : refundedAmount)
                .add(allocatedFromInvoice == null ? BigDecimal.ZERO : allocatedFromInvoice)
                .subtract(allocatedToInvoice == null ? BigDecimal.ZERO : allocatedToInvoice);
    }

    private Long requiredId(Invoice invoice, String name) {
        if (invoice == null || invoice.getId() == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return invoice.getId();
    }

    private ChartOfAccount resolveAccount(ChartOfAccount account, Long companyId, String name) {
        if (account == null) {
            return null;
        }
        if (account.getId() == null) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return accountRepository.findByIdAndCompanyId(account.getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException(name + " not found"));
    }
}
