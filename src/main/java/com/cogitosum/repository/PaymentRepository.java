package com.cogitosum.repository;

import com.cogitosum.entity.Payment;
import com.cogitosum.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTransactionId(String transactionId);
    List<Payment> findByInvoiceId(Long invoiceId);
    List<Payment> findByStatus(PaymentStatus status);
    List<Payment> findByPaymentDateBetween(LocalDate startDate, LocalDate endDate);
    java.util.List<Payment> findByPaymentMethod(com.cogitosum.entity.PaymentMethod paymentMethod);
    Optional<Payment> findByIdAndCompanyId(Long id, Long companyId);
    Optional<Payment> findByCompanyIdAndTransactionId(Long companyId, String transactionId);
    List<Payment> findByCompanyIdAndInvoiceId(Long companyId, Long invoiceId);
    List<Payment> findByCompanyIdAndStatus(Long companyId, PaymentStatus status);
    List<Payment> findByCompanyIdAndPaymentDateBetween(Long companyId, LocalDate startDate, LocalDate endDate);
    List<Payment> findAllByCompanyId(Long companyId);
    List<Payment> findByCompanyIdAndPaymentMethod(Long companyId, com.cogitosum.entity.PaymentMethod paymentMethod);
}
