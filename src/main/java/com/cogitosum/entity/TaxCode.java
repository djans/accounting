package com.cogitosum.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_codes", uniqueConstraints = @UniqueConstraint(
        name = "uk_tax_codes_company_code", columnNames = {"company_id", "code"}))
public class TaxCode implements CompanyOwned {

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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sales_tax_group_id")
    private TaxGroup salesTaxGroup;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "purchase_tax_group_id")
    private TaxGroup purchaseTaxGroup;

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
    public TaxGroup getSalesTaxGroup() { return salesTaxGroup; }
    public void setSalesTaxGroup(TaxGroup salesTaxGroup) { this.salesTaxGroup = salesTaxGroup; }
    public TaxGroup getPurchaseTaxGroup() { return purchaseTaxGroup; }
    public void setPurchaseTaxGroup(TaxGroup purchaseTaxGroup) { this.purchaseTaxGroup = purchaseTaxGroup; }
    public Boolean getActive() { return isActive; }
    public void setActive(Boolean active) { isActive = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
