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
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    List<Invoice> findByCustomerId(Long customerId);
    List<Invoice> findByStatus(InvoiceStatus status);
    List<Invoice> findByInvoiceDateBetween(LocalDate startDate, LocalDate endDate);
    List<Invoice> findByCustomerIdAndStatus(Long customerId, InvoiceStatus status);
}

