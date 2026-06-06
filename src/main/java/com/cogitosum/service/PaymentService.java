package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.Payment;
import com.cogitosum.entity.PaymentStatus;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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

    public Payment recordPayment(Payment payment) {
        if (payment.getTransactionId() == null || payment.getTransactionId().isEmpty()) {
            payment.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (payment.getPaymentDate() == null) {
            payment.setPaymentDate(LocalDate.now());
        }

        Payment savedPayment = paymentRepository.save(payment);

        // Post Dr Bank / Cr A/R if a bank account was provided
        paymentPostingService.postPayment(savedPayment);

        updateInvoicePaymentStatus(payment.getInvoice().getId(), payment.getAmount());

        return savedPayment;
    }

    public Optional<Payment> getPaymentById(Long id) {
        return paymentRepository.findById(id);
    }

    public Optional<Payment> getPaymentByTransactionId(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId);
    }

    public List<Payment> getPaymentsByInvoiceId(Long invoiceId) {
        return paymentRepository.findByInvoiceId(invoiceId);
    }

    public List<Payment> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByStatus(status);
    }

    public List<Payment> getPaymentsByDateRange(LocalDate startDate, LocalDate endDate) {
        return paymentRepository.findByPaymentDateBetween(startDate, endDate);
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Payment markPaymentAsCompleted(Long id) {
        Optional<Payment> payment = paymentRepository.findById(id);
        if (payment.isPresent()) {
            payment.get().setStatus(PaymentStatus.COMPLETED);
            Payment savedPayment = paymentRepository.save(payment.get());
            updateInvoicePaymentStatus(payment.get().getInvoice().getId(), payment.get().getAmount());
            return savedPayment;
        }
        return null;
    }

    public Payment refundPayment(Long id) {
        Optional<Payment> payment = paymentRepository.findById(id);
        if (payment.isPresent()) {
            Payment p = payment.get();
            paymentPostingService.reversePayment(p, "Payment refunded");
            p.setStatus(PaymentStatus.REFUNDED);
            Payment savedPayment = paymentRepository.save(p);

            Invoice invoice = p.getInvoice();
            BigDecimal newPaidAmount = invoice.getPaidAmount().subtract(p.getAmount());
            invoice.setPaidAmount(newPaidAmount);
            invoiceRepository.save(invoice);

            return savedPayment;
        }
        return null;
    }

    public void deletePayment(Long id) {
        Optional<Payment> payment = paymentRepository.findById(id);
        payment.ifPresent(p -> paymentPostingService.reversePayment(p, "Payment deleted"));
        paymentRepository.deleteById(id);
    }

    private void updateInvoicePaymentStatus(Long invoiceId, BigDecimal paymentAmount) {
        Optional<Invoice> invoice = invoiceRepository.findById(invoiceId);
        if (invoice.isPresent()) {
            Invoice inv = invoice.get();
            BigDecimal newPaidAmount = inv.getPaidAmount().add(paymentAmount);
            inv.setPaidAmount(newPaidAmount);

            // Update invoice status based on payment progress
            if (newPaidAmount.compareTo(inv.getTotalAmount()) >= 0) {
                inv.setStatus(InvoiceStatus.PAID);
            } else if (newPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
                inv.setStatus(InvoiceStatus.PARTIALLY_PAID);
            }

            invoiceRepository.save(inv);
        }
    }
}

