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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tax_agency_id")
    private TaxAgency taxAgency;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tax_item_id")
    private TaxItem taxItem;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal debit;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal credit;

    @Column(nullable = false)
    private boolean cleared = false;

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

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    public TaxAgency getTaxAgency() {
        return taxAgency;
    }

    public void setTaxAgency(TaxAgency taxAgency) {
        this.taxAgency = taxAgency;
    }

    public TaxItem getTaxItem() {
        return taxItem;
    }

    public void setTaxItem(TaxItem taxItem) {
        this.taxItem = taxItem;
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

    public boolean isCleared() {
        return cleared;
    }

    public void setCleared(boolean cleared) {
        this.cleared = cleared;
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
