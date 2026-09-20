package com.cogitosum.repository;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findAllByOrderByInvoiceNumberDesc();
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    Optional<Invoice> findTopByOrderByIdDesc();
    List<Invoice> findByCustomerIdOrderByInvoiceNumberDesc(Long customerId);
    List<Invoice> findByStatusOrderByInvoiceNumberDesc(InvoiceStatus status);
    List<Invoice> findByInvoiceDateBetweenOrderByInvoiceDateDesc(LocalDate startDate, LocalDate endDate);
    List<Invoice> findByCustomerIdAndStatusOrderByInvoiceNumberDesc(Long customerId, InvoiceStatus status);
}
