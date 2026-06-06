package com.cogitosum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "general_ledger")
public class GeneralLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    private ChartOfAccount account;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal debitBalance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal creditBalance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @PrePersist
    protected void onCreate() {
        lastUpdated = LocalDateTime.now();
        if (debitBalance == null) {
            debitBalance = BigDecimal.ZERO;
        }
        if (creditBalance == null) {
            creditBalance = BigDecimal.ZERO;
        }
        calculateBalance();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
        calculateBalance();
    }

    private void calculateBalance() {
        if (account == null) return;
        BigDecimal dr = debitBalance == null ? BigDecimal.ZERO : debitBalance;
        BigDecimal cr = creditBalance == null ? BigDecimal.ZERO : creditBalance;
        switch (account.getAccountType()) {
            case ASSET, EXPENSE, CONTRA_LIABILITY -> this.balance = dr.subtract(cr);
            case LIABILITY, EQUITY, REVENUE, CONTRA_ASSET -> this.balance = cr.subtract(dr);
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ChartOfAccount getAccount() {
        return account;
    }

    public void setAccount(ChartOfAccount account) {
        this.account = account;
        calculateBalance();
    }

    public BigDecimal getDebitBalance() {
        return debitBalance;
    }

    public void setDebitBalance(BigDecimal debitBalance) {
        this.debitBalance = debitBalance;
        calculateBalance();
    }

    public BigDecimal getCreditBalance() {
        return creditBalance;
    }

    public void setCreditBalance(BigDecimal creditBalance) {
        this.creditBalance = creditBalance;
        calculateBalance();
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}

