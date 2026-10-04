package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.Payment;
import com.cogitosum.entity.PaymentStatus;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.PaymentRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
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

    @Transactional
    public Payment recordPayment(Payment payment) {
        Long companyId = companyContext.requireCompanyId();
        payment.setCompany(companyContext.requireCompany());
        payment.setInvoice(invoiceRepository.findByIdAndCompanyId(requiredId(payment.getInvoice(), "Invoice"), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found")));
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
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new IllegalStateException("Invoice not found for payment"));
        BigDecimal openingPaidAmount = invoice.getOpeningPaidAmount() == null
                ? BigDecimal.ZERO : invoice.getOpeningPaidAmount();
        BigDecimal paidAmount = paymentRepository.findByCompanyIdAndInvoiceId(companyId, invoiceId).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING
                        || payment.getStatus() == PaymentStatus.COMPLETED)
                .map(Payment::getAmount)
                .reduce(openingPaidAmount, BigDecimal::add);

        invoice.setPaidAmount(paidAmount);
        if (invoice.getStatus() != InvoiceStatus.CANCELLED && invoice.getStatus() != InvoiceStatus.DRAFT) {
            if (paidAmount.compareTo(invoice.getTotalAmount()) >= 0) {
                invoice.setStatus(InvoiceStatus.PAID);
            } else if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
                invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
            } else if (invoice.getStatus() == InvoiceStatus.PAID
                    || invoice.getStatus() == InvoiceStatus.PARTIALLY_PAID
                    || invoice.getStatus() == InvoiceStatus.REFUNDED) {
                invoice.setStatus(InvoiceStatus.SENT);
            }
        }
        invoiceRepository.save(invoice);
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
