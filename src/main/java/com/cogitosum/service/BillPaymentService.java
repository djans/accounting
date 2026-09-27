package com.cogitosum.service;

import com.cogitosum.entity.Bill;
import com.cogitosum.entity.BillStatus;
import com.cogitosum.entity.BillPayment;
import com.cogitosum.entity.PaymentStatus;
import com.cogitosum.repository.BillRepository;
import com.cogitosum.repository.BillPaymentRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.entity.ChartOfAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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

    @Autowired
    private BillPaymentRepository paymentRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillPaymentPostingService billPaymentPostingService;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private CurrentCompanyContext companyContext;

    public BillPayment recordPayment(BillPayment payment) {
        Long companyId = companyContext.requireCompanyId();
        payment.setCompany(companyContext.requireCompany());
        if (payment.getBill() == null || payment.getBill().getId() == null) {
            throw new IllegalArgumentException("Bill is required");
        }
        payment.setBill(billRepository.findByIdAndCompanyId(payment.getBill().getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found")));
        payment.setBankAccount(resolveAccount(payment.getBankAccount(), companyId));
        if (payment.getTransactionId() == null || payment.getTransactionId().isEmpty()) {
            payment.setTransactionId("VTXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (payment.getPaymentDate() == null) {
            payment.setPaymentDate(LocalDate.now());
        }

        BillPayment savedPayment = paymentRepository.save(payment);

        // Post Dr A/P / Cr Bank if a bank account was provided
        billPaymentPostingService.postPayment(savedPayment);

        updateBillPaymentStatus(payment.getBill().getId(), payment.getAmount());

        return savedPayment;
    }

    public Optional<BillPayment> getPaymentById(Long id) {
        return paymentRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public List<BillPayment> getPaymentsByBillId(Long billId) {
        return paymentRepository.findByCompanyIdAndBillId(companyContext.requireCompanyId(), billId);
    }

    public List<BillPayment> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByCompanyIdAndStatus(companyContext.requireCompanyId(), status);
    }

    public List<BillPayment> getPaymentsByDateRange(LocalDate startDate, LocalDate endDate) {
        return paymentRepository.findByCompanyIdAndPaymentDateBetween(companyContext.requireCompanyId(), startDate, endDate);
    }

    public List<BillPayment> getAllPayments() {
        return paymentRepository.findAllByCompanyId(companyContext.requireCompanyId());
    }

    public BillPayment markPaymentAsCompleted(Long id) {
        Optional<BillPayment> payment = paymentRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
        if (payment.isPresent()) {
            payment.get().setStatus(PaymentStatus.COMPLETED);
            BillPayment savedPayment = paymentRepository.save(payment.get());
            updateBillPaymentStatus(payment.get().getBill().getId(), payment.get().getAmount());
            return savedPayment;
        }
        return null;
    }

    public BillPayment refundPayment(Long id) {
        Optional<BillPayment> payment = paymentRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
        if (payment.isPresent()) {
            BillPayment p = payment.get();
            billPaymentPostingService.reversePayment(p, "Payment refunded");
            p.setStatus(PaymentStatus.REFUNDED);
            BillPayment savedPayment = paymentRepository.save(p);

            Bill bill = p.getBill();
            BigDecimal newPaidAmount = bill.getPaidAmount().subtract(p.getAmount());
            bill.setPaidAmount(newPaidAmount);
            billRepository.save(bill);

            return savedPayment;
        }
        return null;
    }

    public void deletePayment(Long id) {
        Optional<BillPayment> payment = paymentRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
        payment.ifPresent(p -> billPaymentPostingService.reversePayment(p, "Payment deleted"));
        payment.ifPresent(paymentRepository::delete);
    }

    private void updateBillPaymentStatus(Long billId, BigDecimal paymentAmount) {
        Optional<Bill> bill = billRepository.findByIdAndCompanyId(billId, companyContext.requireCompanyId());
        if (bill.isPresent()) {
            Bill b = bill.get();
            BigDecimal newPaidAmount = b.getPaidAmount().add(paymentAmount);
            b.setPaidAmount(newPaidAmount);

            if (newPaidAmount.compareTo(b.getTotalAmount()) >= 0) {
                b.setStatus(BillStatus.PAID);
            } else if (newPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
                b.setStatus(BillStatus.PARTIALLY_PAID);
            }

            billRepository.save(b);
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
