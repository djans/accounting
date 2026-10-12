package com.cogitosum.service;

import com.cogitosum.entity.Bill;
import com.cogitosum.entity.BillPayment;
import com.cogitosum.entity.BillStatus;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.PaymentStatus;
import com.cogitosum.repository.BillPaymentRepository;
import com.cogitosum.repository.BillRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Records payments made to vendors against bills — mirrors {@link PaymentService}.
 */
@Service
public class BillPaymentService {

    private final BillPaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final BillPaymentPostingService billPaymentPostingService;
    private final ChartOfAccountRepository accountRepository;
    private final CurrentCompanyContext companyContext;

    public BillPaymentService(BillPaymentRepository paymentRepository,
                              BillRepository billRepository,
                              BillPaymentPostingService billPaymentPostingService,
                              ChartOfAccountRepository accountRepository,
                              CurrentCompanyContext companyContext) {
        this.paymentRepository = paymentRepository;
        this.billRepository = billRepository;
        this.billPaymentPostingService = billPaymentPostingService;
        this.accountRepository = accountRepository;
        this.companyContext = companyContext;
    }

    @Transactional
    public BillPayment recordPayment(BillPayment payment) {
        Long companyId = companyContext.requireCompanyId();
        if (payment == null || payment.getBill() == null || payment.getBill().getId() == null) {
            throw new IllegalArgumentException("Bill is required");
        }
        if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        Bill bill = billRepository.findByIdAndCompanyId(payment.getBill().getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        requireOpenBill(bill);
        payment.setCompany(companyContext.requireCompany());
        payment.setBill(bill);
        payment.setBankAccount(resolveAccount(payment.getBankAccount(), companyId));
        payment.setStatus(PaymentStatus.PENDING);
        if (payment.getTransactionId() == null || payment.getTransactionId().isBlank()) {
            payment.setTransactionId("VTXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (payment.getPaymentDate() == null) {
            payment.setPaymentDate(LocalDate.now());
        }

        BillPayment savedPayment = paymentRepository.save(payment);
        billPaymentPostingService.postPayment(savedPayment);
        adjustBillPaymentStatus(bill, savedPayment.getAmount());
        return savedPayment;
    }

    @Transactional(readOnly = true)
    public Optional<BillPayment> getPaymentById(Long id) {
        return paymentRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    @Transactional(readOnly = true)
    public List<BillPayment> getPaymentsByBillId(Long billId) {
        return paymentRepository.findByCompanyIdAndBillId(companyContext.requireCompanyId(), billId);
    }

    @Transactional(readOnly = true)
    public List<BillPayment> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByCompanyIdAndStatus(companyContext.requireCompanyId(), status);
    }

    @Transactional(readOnly = true)
    public List<BillPayment> getPaymentsByDateRange(LocalDate startDate, LocalDate endDate) {
        return paymentRepository.findByCompanyIdAndPaymentDateBetween(
                companyContext.requireCompanyId(), startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<BillPayment> getAllPayments() {
        return paymentRepository.findAllByCompanyId(companyContext.requireCompanyId());
    }

    @Transactional
    public BillPayment markPaymentAsCompleted(Long id) {
        BillPayment payment = paymentRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return payment;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending payments can be completed");
        }
        payment.setStatus(PaymentStatus.COMPLETED);
        return paymentRepository.save(payment);
    }

    @Transactional
    public BillPayment refundPayment(Long id) {
        BillPayment payment = paymentRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        requireActivePayment(payment);

        billPaymentPostingService.reversePayment(payment, "Bill payment refunded");
        payment.setStatus(PaymentStatus.REFUNDED);
        BillPayment savedPayment = paymentRepository.save(payment);
        adjustBillPaymentStatus(payment.getBill(), payment.getAmount().negate());
        return savedPayment;
    }

    @Transactional
    public void deletePayment(Long id) {
        BillPayment payment = paymentRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        requireActivePayment(payment);

        billPaymentPostingService.reversePayment(payment, "Bill payment voided");
        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);
        adjustBillPaymentStatus(payment.getBill(), payment.getAmount().negate());
    }

    private void adjustBillPaymentStatus(Bill bill, BigDecimal amountDelta) {
        BigDecimal currentPaid = bill.getPaidAmount() == null ? BigDecimal.ZERO : bill.getPaidAmount();
        BigDecimal updatedPaid = currentPaid.add(amountDelta);
        if (updatedPaid.signum() < 0) {
            throw new IllegalStateException("Bill payment total cannot be negative");
        }
        bill.setPaidAmount(updatedPaid);
        if (updatedPaid.compareTo(bill.getTotalAmount()) >= 0) {
            bill.setStatus(BillStatus.PAID);
        } else if (updatedPaid.signum() > 0) {
            bill.setStatus(BillStatus.PARTIALLY_PAID);
        } else if (bill.getStatus() == BillStatus.PAID || bill.getStatus() == BillStatus.PARTIALLY_PAID) {
            bill.setStatus(BillStatus.RECEIVED);
        }
        billRepository.save(bill);
    }

    private void requireOpenBill(Bill bill) {
        if (bill.getStatus() != BillStatus.RECEIVED
                && bill.getStatus() != BillStatus.PARTIALLY_PAID
                && bill.getStatus() != BillStatus.OVERDUE) {
            throw new IllegalStateException("Only received bills can be paid");
        }
    }

    private void requireActivePayment(BillPayment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Only active payments can be reversed");
        }
    }

    private ChartOfAccount resolveAccount(ChartOfAccount account, Long companyId) {
        if (account == null) {
            return null;
        }
        if (account.getId() == null) {
            throw new IllegalArgumentException("Bank account is invalid");
        }
        return accountRepository.findByIdAndCompanyId(account.getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));
    }
}
