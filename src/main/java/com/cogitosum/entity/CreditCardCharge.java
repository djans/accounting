package com.cogitosum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_card_charges")
public class CreditCardCharge implements CompanyOwned {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(nullable = false)
    private LocalDate chargeDate;

    @Column(length = 1000)
    private String memo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "expense_account_id", nullable = false)
    private ChartOfAccount expenseAccount;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "card_account_id", nullable = false)
    private ChartOfAccount cardAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, length = 30)
    private String taxRegime;

    @OneToOne
    @JoinColumn(name = "journal_id")
    private GeneralJournal journal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CreditCardChargeStatus status = CreditCardChargeStatus.DRAFT;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reissue_of_id")
    private CreditCardCharge reissueOf;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = CreditCardChargeStatus.DRAFT;
        }
    }

    public Long getId() { return id; }
    @Override
    public Company getCompany() { return company; }
    @Override
    public void setCompany(Company value) { company = value; }
    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }
    public LocalDate getChargeDate() { return chargeDate; }
    public void setChargeDate(LocalDate chargeDate) { this.chargeDate = chargeDate; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public ChartOfAccount getExpenseAccount() { return expenseAccount; }
    public void setExpenseAccount(ChartOfAccount expenseAccount) { this.expenseAccount = expenseAccount; }
    public ChartOfAccount getCardAccount() { return cardAccount; }
    public void setCardAccount(ChartOfAccount cardAccount) { this.cardAccount = cardAccount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public String getTaxRegime() { return taxRegime; }
    public void setTaxRegime(String taxRegime) { this.taxRegime = taxRegime; }
    public GeneralJournal getJournal() { return journal; }
    public void setJournal(GeneralJournal journal) { this.journal = journal; }
    public CreditCardChargeStatus getStatus() { return status; }
    public void setStatus(CreditCardChargeStatus status) { this.status = status; }
    public LocalDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(LocalDateTime voidedAt) { this.voidedAt = voidedAt; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public CreditCardCharge getReissueOf() { return reissueOf; }
    public void setReissueOf(CreditCardCharge reissueOf) { this.reissueOf = reissueOf; }
}
