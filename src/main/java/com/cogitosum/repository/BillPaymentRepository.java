package com.cogitosum.repository;

import com.cogitosum.entity.BillPayment;
import com.cogitosum.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {
    Optional<BillPayment> findByTransactionId(String transactionId);
    List<BillPayment> findByBillId(Long billId);
    List<BillPayment> findByStatus(PaymentStatus status);
    List<BillPayment> findByPaymentDateBetween(LocalDate startDate, LocalDate endDate);
    Optional<BillPayment> findByIdAndCompanyId(Long id, Long companyId);
    Optional<BillPayment> findByCompanyIdAndTransactionId(Long companyId, String transactionId);
    List<BillPayment> findByCompanyIdAndBillId(Long companyId, Long billId);
    List<BillPayment> findByCompanyIdAndStatus(Long companyId, PaymentStatus status);
    List<BillPayment> findByCompanyIdAndPaymentDateBetween(Long companyId, LocalDate startDate, LocalDate endDate);
    List<BillPayment> findAllByCompanyId(Long companyId);
}
