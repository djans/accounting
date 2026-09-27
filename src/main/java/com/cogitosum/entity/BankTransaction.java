package com.cogitosum.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "bank_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bank_transactions_company_account_row",
                columnNames = {"company_id", "bank_account_id", "source_row_hash"}))
public class BankTransaction implements CompanyOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false, updatable = false)
    private ChartOfAccount bankAccount;

    @Column(nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String reference;

    @Column(nullable = false, length = 64)
    private String sourceHash;

    @Column(nullable = false, length = 64)
    private String sourceRowHash;

    @Column(nullable = false)
    private boolean reconciled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_session_id")
    private BankReconciliationSession reconciliationSession;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
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

    public ChartOfAccount getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(ChartOfAccount bankAccount) {
        this.bankAccount = bankAccount;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public void setSourceHash(String sourceHash) {
        this.sourceHash = sourceHash;
    }

    public String getSourceRowHash() {
        return sourceRowHash;
    }

    public void setSourceRowHash(String sourceRowHash) {
        this.sourceRowHash = sourceRowHash;
    }

    public boolean isReconciled() {
        return reconciled;
    }

    public void setReconciled(boolean reconciled) {
        this.reconciled = reconciled;
    }

    public BankReconciliationSession getReconciliationSession() {
        return reconciliationSession;
    }

    public void setReconciliationSession(BankReconciliationSession reconciliationSession) {
        this.reconciliationSession = reconciliationSession;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
