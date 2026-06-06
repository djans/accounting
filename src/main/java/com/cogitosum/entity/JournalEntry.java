package com.cogitosum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "journal_id", nullable = false)
    private GeneralJournal journal;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    private ChartOfAccount account;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal debit;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal credit;
    
    @Column(length = 500)
    private String description;
    
    @Column(nullable = false)
    private Integer lineNumber;
    
    @PrePersist
    protected void onCreate() {
        if (debit == null) {
            debit = BigDecimal.ZERO;
        }
        if (credit == null) {
            credit = BigDecimal.ZERO;
        }
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public GeneralJournal getJournal() {
        return journal;
    }
    
    public void setJournal(GeneralJournal journal) {
        this.journal = journal;
    }
    
    public ChartOfAccount getAccount() {
        return account;
    }
    
    public void setAccount(ChartOfAccount account) {
        this.account = account;
    }
    
    public BigDecimal getDebit() {
        return debit;
    }
    
    public void setDebit(BigDecimal debit) {
        this.debit = debit;
    }
    
    public BigDecimal getCredit() {
        return credit;
    }
    
    public void setCredit(BigDecimal credit) {
        this.credit = credit;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Integer getLineNumber() {
        return lineNumber;
    }
    
    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }
}

