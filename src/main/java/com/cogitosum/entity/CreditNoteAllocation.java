package com.cogitosum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_note_allocations")
public class CreditNoteAllocation implements CompanyOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false, updatable = false)
    private CreditNote creditNote;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "target_invoice_id", nullable = false, updatable = false)
    private Invoice targetInvoice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "allocation_date", nullable = false)
    private LocalDate allocationDate;

    @Column(name = "reversal_date")
    private LocalDate reversalDate;

    @Column(name = "reversal_reason", length = 1000)
    private String reversalReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public Company getCompany() {
        return company;
    }

    @Override
    public void setCompany(Company company) {
        this.company = company;
    }

    public CreditNote getCreditNote() {
        return creditNote;
    }

    public void setCreditNote(CreditNote creditNote) {
        this.creditNote = creditNote;
    }

    public Invoice getTargetInvoice() {
        return targetInvoice;
    }

    public void setTargetInvoice(Invoice targetInvoice) {
        this.targetInvoice = targetInvoice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getAllocationDate() {
        return allocationDate;
    }

    public void setAllocationDate(LocalDate allocationDate) {
        this.allocationDate = allocationDate;
    }

    public LocalDate getReversalDate() {
        return reversalDate;
    }

    public void setReversalDate(LocalDate reversalDate) {
        this.reversalDate = reversalDate;
    }

    public String getReversalReason() {
        return reversalReason;
    }

    public void setReversalReason(String reversalReason) {
        this.reversalReason = reversalReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
