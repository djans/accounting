package com.cogitosum.repository;

import com.cogitosum.entity.InvoiceFinalPdf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceFinalPdfRepository extends JpaRepository<InvoiceFinalPdf, Long> {
}
