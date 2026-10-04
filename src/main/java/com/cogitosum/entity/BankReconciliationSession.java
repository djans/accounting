package com.cogitosum.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_reconciliation_sessions")
public class BankReconciliationSession implements CompanyOwned {

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
    private LocalDate statementDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal transactionTotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal endingBalance;

    @Column(name = "register_balance", precision = 19, scale = 2)
    private BigDecimal registerBalance;

    @Column(name = "report_lines_captured", nullable = false)
    private boolean reportLinesCaptured;

    @Column(nullable = false)
    private LocalDateTime completedAt;

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

    public LocalDate getStatementDate() {
        return statementDate;
    }

    public void setStatementDate(LocalDate statementDate) {
        this.statementDate = statementDate;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public BigDecimal getTransactionTotal() {
        return transactionTotal;
    }

    public void setTransactionTotal(BigDecimal transactionTotal) {
        this.transactionTotal = transactionTotal;
    }

    public BigDecimal getEndingBalance() {
        return endingBalance;
    }

    public void setEndingBalance(BigDecimal endingBalance) {
        this.endingBalance = endingBalance;
    }

    public BigDecimal getRegisterBalance() {
        return registerBalance;
    }

    public void setRegisterBalance(BigDecimal registerBalance) {
        this.registerBalance = registerBalance;
    }

    public boolean isReportLinesCaptured() {
        return reportLinesCaptured;
    }

    public void setReportLinesCaptured(boolean reportLinesCaptured) {
        this.reportLinesCaptured = reportLinesCaptured;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
