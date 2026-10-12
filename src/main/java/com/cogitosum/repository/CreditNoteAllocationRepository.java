package com.cogitosum.repository;

import com.cogitosum.entity.CreditNoteAllocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CreditNoteAllocationRepository extends JpaRepository<CreditNoteAllocation, Long> {

    List<CreditNoteAllocation> findByCompanyIdAndCreditNoteIdOrderByAllocationDateDescIdDesc(
            Long companyId, Long creditNoteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select allocation from CreditNoteAllocation allocation "
            + "where allocation.id = :id and allocation.company.id = :companyId")
    Optional<CreditNoteAllocation> findLockedByIdAndCompanyId(
            @Param("id") Long id, @Param("companyId") Long companyId);

    boolean existsByCompanyIdAndTargetInvoiceIdAndReversalDateIsNull(Long companyId, Long targetInvoiceId);

    @Query("select coalesce(sum(allocation.amount), 0) from CreditNoteAllocation allocation "
            + "where allocation.company.id = :companyId and allocation.creditNote.id = :creditNoteId "
            + "and allocation.reversalDate is null")
    BigDecimal sumByCompanyIdAndCreditNoteId(
            @Param("companyId") Long companyId, @Param("creditNoteId") Long creditNoteId);

    @Query("select coalesce(sum(allocation.amount), 0) from CreditNoteAllocation allocation "
            + "where allocation.company.id = :companyId and allocation.creditNote.invoice.id = :invoiceId "
            + "and allocation.reversalDate is null")
    BigDecimal sumByCompanyIdAndSourceInvoiceId(
            @Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId);

    @Query("select coalesce(sum(allocation.amount), 0) from CreditNoteAllocation allocation "
            + "where allocation.company.id = :companyId and allocation.targetInvoice.id = :invoiceId "
            + "and allocation.reversalDate is null")
    BigDecimal sumByCompanyIdAndTargetInvoiceId(
            @Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId);

    @Query("select coalesce(sum(allocation.amount), 0) from CreditNoteAllocation allocation "
            + "where allocation.company.id = :companyId and allocation.creditNote.invoice.id = :invoiceId "
            + "and allocation.allocationDate <= :asOf "
            + "and (allocation.reversalDate is null or allocation.reversalDate > :asOf)")
    BigDecimal sumByCompanyIdAndSourceInvoiceIdBeforeDate(
            @Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId,
            @Param("asOf") LocalDate asOf);

    @Query("select coalesce(sum(allocation.amount), 0) from CreditNoteAllocation allocation "
            + "where allocation.company.id = :companyId and allocation.targetInvoice.id = :invoiceId "
            + "and allocation.allocationDate <= :asOf "
            + "and (allocation.reversalDate is null or allocation.reversalDate > :asOf)")
    BigDecimal sumByCompanyIdAndTargetInvoiceIdBeforeDate(
            @Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId,
            @Param("asOf") LocalDate asOf);

    @Query("select allocation from CreditNoteAllocation allocation "
            + "where allocation.company.id = :companyId and allocation.allocationDate <= :endDate "
            + "order by allocation.allocationDate, allocation.id")
    List<CreditNoteAllocation> findByCompanyIdAndAllocationDateLessThanEqualOrderByAllocationDate(
            @Param("companyId") Long companyId, @Param("endDate") LocalDate endDate);
}
