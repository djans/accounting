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
    Long countByTaxRegime(String taxRegime);
    Optional<Invoice> findByIdAndCompanyId(Long id, Long companyId);
    List<Invoice> findAllByCompanyIdOrderByInvoiceNumberDesc(Long companyId);
    Optional<Invoice> findByCompanyIdAndInvoiceNumber(Long companyId, String invoiceNumber);
    Optional<Invoice> findTopByCompanyIdOrderByIdDesc(Long companyId);
    List<Invoice> findByCompanyIdAndCustomerIdOrderByInvoiceNumberDesc(Long companyId, Long customerId);
    List<Invoice> findByCompanyIdAndStatusOrderByInvoiceNumberDesc(Long companyId, InvoiceStatus status);
    List<Invoice> findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(Long companyId, LocalDate startDate, LocalDate endDate);
    Long countByCompanyIdAndTaxRegime(Long companyId, String taxRegime);
}
