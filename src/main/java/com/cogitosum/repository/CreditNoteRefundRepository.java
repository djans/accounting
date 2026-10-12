package com.cogitosum.repository;

import com.cogitosum.entity.CreditNoteRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CreditNoteRefundRepository extends JpaRepository<CreditNoteRefund, Long> {

    List<CreditNoteRefund> findByCompanyIdAndCreditNoteIdOrderByRefundDateDescIdDesc(
            Long companyId, Long creditNoteId);

    boolean existsByCompanyIdAndTransactionId(Long companyId, String transactionId);

    @Query("select coalesce(sum(refund.amount), 0) from CreditNoteRefund refund "
            + "where refund.company.id = :companyId and refund.creditNote.id = :creditNoteId")
    BigDecimal sumByCompanyIdAndCreditNoteId(
            @Param("companyId") Long companyId, @Param("creditNoteId") Long creditNoteId);

    @Query("select coalesce(sum(refund.amount), 0) from CreditNoteRefund refund "
            + "where refund.company.id = :companyId and refund.creditNote.invoice.id = :invoiceId")
    BigDecimal sumByCompanyIdAndInvoiceId(
            @Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId);

    @Query("select coalesce(sum(refund.amount), 0) from CreditNoteRefund refund "
            + "where refund.company.id = :companyId and refund.creditNote.invoice.id = :invoiceId "
            + "and refund.refundDate <= :asOf")
    BigDecimal sumByCompanyIdAndInvoiceIdBeforeDate(
            @Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId,
            @Param("asOf") java.time.LocalDate asOf);

    @Query("select refund from CreditNoteRefund refund "
            + "where refund.company.id = :companyId and refund.refundDate <= :endDate "
            + "order by refund.refundDate, refund.id")
    List<CreditNoteRefund> findByCompanyIdAndRefundDateLessThanEqualOrderByRefundDate(
            @Param("companyId") Long companyId, @Param("endDate") java.time.LocalDate endDate);
}
