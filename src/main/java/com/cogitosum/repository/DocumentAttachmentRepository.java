package com.cogitosum.repository;

import com.cogitosum.entity.DocumentAttachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachment, Long> {

    List<DocumentAttachment> findAllByCompanyIdAndInvoiceIdOrderByCreatedAtDesc(Long companyId, Long invoiceId);

    List<DocumentAttachment> findAllByCompanyIdAndBillIdOrderByCreatedAtDesc(Long companyId, Long billId);

    Optional<DocumentAttachment> findByIdAndCompanyIdAndInvoiceId(Long id, Long companyId, Long invoiceId);

    Optional<DocumentAttachment> findByIdAndCompanyIdAndBillId(Long id, Long companyId, Long billId);
}
