package com.cogitosum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_filing_periods")
public class TaxFilingPeriod implements CompanyOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agency_id", nullable = false)
    private TaxAgency agency;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaxFilingStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal taxCollected;

    @Column(name = "tax_itc", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxItc;

    @Column(name = "net_owing", nullable = false, precision = 19, scale = 2)
    private BigDecimal netOwing;

    @Column(name = "filed_date")
    private LocalDate filedDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "filing_journal_id")
    private GeneralJournal filingJournal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_journal_id")
    private GeneralJournal paymentJournal;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = TaxFilingStatus.OPEN;
        if (taxCollected == null) taxCollected = BigDecimal.ZERO;
        if (taxItc == null) taxItc = BigDecimal.ZERO;
        if (netOwing == null) netOwing = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    @Override
    public Company getCompany() { return company; }
    @Override
    public void setCompany(Company company) { this.company = company; }
    public TaxAgency getAgency() { return agency; }
    public void setAgency(TaxAgency agency) { this.agency = agency; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public TaxFilingStatus getStatus() { return status; }
    public void setStatus(TaxFilingStatus status) { this.status = status; }
    public BigDecimal getTaxCollected() { return taxCollected; }
    public void setTaxCollected(BigDecimal taxCollected) { this.taxCollected = taxCollected; }
    public BigDecimal getTaxItc() { return taxItc; }
    public void setTaxItc(BigDecimal taxItc) { this.taxItc = taxItc; }
    public BigDecimal getNetOwing() { return netOwing; }
    public void setNetOwing(BigDecimal netOwing) { this.netOwing = netOwing; }
    public LocalDate getFiledDate() { return filedDate; }
    public void setFiledDate(LocalDate filedDate) { this.filedDate = filedDate; }
    public LocalDate getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDate paidDate) { this.paidDate = paidDate; }
    public GeneralJournal getFilingJournal() { return filingJournal; }
    public void setFilingJournal(GeneralJournal filingJournal) { this.filingJournal = filingJournal; }
    public GeneralJournal getPaymentJournal() { return paymentJournal; }
    public void setPaymentJournal(GeneralJournal paymentJournal) { this.paymentJournal = paymentJournal; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
