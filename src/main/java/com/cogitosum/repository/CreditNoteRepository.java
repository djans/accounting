package com.cogitosum.repository;

import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    Optional<CreditNote> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndInvoiceId(Long companyId, Long invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select note from CreditNote note where note.id = :id and note.company.id = :companyId")
    Optional<CreditNote> findLockedByIdAndCompanyId(
            @Param("id") Long id, @Param("companyId") Long companyId);

    List<CreditNote> findAllByCompanyIdOrderByCreditNoteDateDescIdDesc(Long companyId);

    List<CreditNote> findByCompanyIdAndInvoiceIdOrderByCreditNoteDateDescIdDesc(Long companyId, Long invoiceId);

    List<CreditNote> findByCompanyIdAndStatusAndCreditNoteDateBetweenOrderByCreditNoteDateDescIdDesc(
            Long companyId, CreditNoteStatus status, LocalDate startDate, LocalDate endDate);

    List<CreditNote> findByCompanyIdAndStatusAndCreditNoteDateLessThanEqualOrderByCreditNoteDateDescIdDesc(
            Long companyId, CreditNoteStatus status, LocalDate endDate);

    Optional<CreditNote> findTopByCompanyIdOrderByIdDesc(Long companyId);

    @Query("select coalesce(sum(note.totalAmount), 0) from CreditNote note "
            + "where note.company.id = :companyId and note.invoice.id = :invoiceId "
            + "and note.status = :status")
    BigDecimal sumTotalByCompanyIdAndInvoiceIdAndStatus(
            @Param("companyId") Long companyId,
            @Param("invoiceId") Long invoiceId,
            @Param("status") CreditNoteStatus status);

    @Query("select coalesce(sum(note.totalAmount), 0) from CreditNote note "
            + "where note.company.id = :companyId and note.invoice.id = :invoiceId "
            + "and note.status = :status and note.creditNoteDate <= :endDate")
    BigDecimal sumTotalByCompanyIdAndInvoiceIdAndStatusBeforeDate(
            @Param("companyId") Long companyId,
            @Param("invoiceId") Long invoiceId,
            @Param("status") CreditNoteStatus status,
            @Param("endDate") LocalDate endDate);
}
