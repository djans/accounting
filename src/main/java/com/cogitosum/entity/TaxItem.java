package com.cogitosum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_tax_items_company_code", columnNames = {"company_id", "code"}))
public class TaxItem implements CompanyOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 7, scale = 5)
    private BigDecimal rate;

    @Column(nullable = false)
    private Boolean forSales = true;

    @Column(nullable = false)
    private Boolean forPurchases = true;

    @Column(length = 20)
    private String salesReturnLine;

    @Column(length = 20)
    private String purchaseReturnLine;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agency_id", nullable = false)
    private TaxAgency agency;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payable_account_id")
    private ChartOfAccount payableAccount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "itc_account_id")
    private ChartOfAccount itcAccount;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
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
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public Boolean getForSales() { return forSales; }
    public void setForSales(Boolean forSales) { this.forSales = forSales; }
    public Boolean getForPurchases() { return forPurchases; }
    public void setForPurchases(Boolean forPurchases) { this.forPurchases = forPurchases; }
    public String getSalesReturnLine() { return salesReturnLine; }
    public void setSalesReturnLine(String salesReturnLine) { this.salesReturnLine = salesReturnLine; }
    public String getPurchaseReturnLine() { return purchaseReturnLine; }
    public void setPurchaseReturnLine(String purchaseReturnLine) { this.purchaseReturnLine = purchaseReturnLine; }
    public TaxAgency getAgency() { return agency; }
    public void setAgency(TaxAgency agency) { this.agency = agency; }
    public ChartOfAccount getPayableAccount() { return payableAccount; }
    public void setPayableAccount(ChartOfAccount payableAccount) { this.payableAccount = payableAccount; }
    public ChartOfAccount getItcAccount() { return itcAccount; }
    public void setItcAccount(ChartOfAccount itcAccount) { this.itcAccount = itcAccount; }
    public Boolean getActive() { return isActive; }
    public void setActive(Boolean active) { isActive = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
