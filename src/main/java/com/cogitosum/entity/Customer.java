package com.cogitosum.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
public class Customer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String businessName;
    
    @Column(nullable = false)
    private String address;
    
    @Column(nullable = false)
    private String city;
    
    @Column(nullable = false)
    private String province;
    
    @Column(nullable = false)
    private String postalCode;
    
    @Column(nullable = false)
    private String country;
    
    private String businessNumber;
    
    private String gstNumber;

    private String qstNumber;

    private String companyName;
    private String title;
    private String firstName;
    private String middleInitial;
    private String lastName;
    private String jobTitle;
    private String mainPhone;
    private String workPhone;
    private String mobilePhone;
    private String fax;
    private String website;
    private String secondaryEmail;
    private String ccEmail;
    @Column(length = 1000) private String shipToAddress;
    private String accountNumber;
    private String paymentTerms;
    private String billingRateLevel;
    private String printOnChequeAs;
    private java.math.BigDecimal creditLimit;
    
    // Tax settings
    private String taxReturnType;
    private String taxReportingPeriod;
    private String taxPeriodEnding;
    private String taxLabel;
    private String salesTaxRegistrationNumber;
    @ManyToOne(fetch = FetchType.EAGER) private ChartOfAccount salesTaxAccount;
    @ManyToOne(fetch = FetchType.EAGER) private ChartOfAccount purchaseTaxAccount;
    private boolean trackSalesTaxSeparately;
    private boolean trackPurchaseTaxSeparately;
    private boolean taxOnOtherTaxes;
    private boolean taxIncludedOnSales;

    @Column(length = 2000) private String notes;
    private boolean inactive;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getBusinessName() {
        return businessName;
    }
    
    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getCity() {
        return city;
    }
    
    public void setCity(String city) {
        this.city = city;
    }
    
    public String getProvince() {
        return province;
    }
    
    public void setProvince(String province) {
        this.province = province;
    }
    
    public String getPostalCode() {
        return postalCode;
    }
    
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }
    
    public String getCountry() {
        return country;
    }
    
    public void setCountry(String country) {
        this.country = country;
    }
    
    public String getBusinessNumber() {
        return businessNumber;
    }
    
    public void setBusinessNumber(String businessNumber) {
        this.businessNumber = businessNumber;
    }
    
    public String getGstNumber() {
        return gstNumber;
    }
    
    public void setGstNumber(String gstNumber) {
        this.gstNumber = gstNumber;
    }

    public String getQstNumber() {
        return qstNumber;
    }

    public void setQstNumber(String qstNumber) {
        this.qstNumber = qstNumber;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleInitial() {
        return middleInitial;
    }

    public void setMiddleInitial(String middleInitial) {
        this.middleInitial = middleInitial;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getMainPhone() {
        return mainPhone;
    }

    public void setMainPhone(String mainPhone) {
        this.mainPhone = mainPhone;
    }

    public String getWorkPhone() {
        return workPhone;
    }

    public void setWorkPhone(String workPhone) {
        this.workPhone = workPhone;
    }

    public String getMobilePhone() {
        return mobilePhone;
    }

    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }

    public String getFax() {
        return fax;
    }

    public void setFax(String fax) {
        this.fax = fax;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getSecondaryEmail() {
        return secondaryEmail;
    }

    public void setSecondaryEmail(String secondaryEmail) {
        this.secondaryEmail = secondaryEmail;
    }

    public String getCcEmail() {
        return ccEmail;
    }

    public void setCcEmail(String ccEmail) {
        this.ccEmail = ccEmail;
    }

    public String getShipToAddress() {
        return shipToAddress;
    }

    public void setShipToAddress(String shipToAddress) {
        this.shipToAddress = shipToAddress;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public String getBillingRateLevel() {
        return billingRateLevel;
    }

    public void setBillingRateLevel(String billingRateLevel) {
        this.billingRateLevel = billingRateLevel;
    }

    public String getPrintOnChequeAs() {
        return printOnChequeAs;
    }

    public void setPrintOnChequeAs(String printOnChequeAs) {
        this.printOnChequeAs = printOnChequeAs;
    }

    public java.math.BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(java.math.BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public String getTaxReturnType() {
        return taxReturnType;
    }

    public void setTaxReturnType(String taxReturnType) {
        this.taxReturnType = taxReturnType;
    }

    public String getTaxReportingPeriod() {
        return taxReportingPeriod;
    }

    public void setTaxReportingPeriod(String taxReportingPeriod) {
        this.taxReportingPeriod = taxReportingPeriod;
    }

    public String getTaxPeriodEnding() {
        return taxPeriodEnding;
    }

    public void setTaxPeriodEnding(String taxPeriodEnding) {
        this.taxPeriodEnding = taxPeriodEnding;
    }

    public String getTaxLabel() {
        return taxLabel;
    }

    public void setTaxLabel(String taxLabel) {
        this.taxLabel = taxLabel;
    }

    public String getSalesTaxRegistrationNumber() {
        return salesTaxRegistrationNumber;
    }

    public void setSalesTaxRegistrationNumber(String salesTaxRegistrationNumber) {
        this.salesTaxRegistrationNumber = salesTaxRegistrationNumber;
    }

    public ChartOfAccount getSalesTaxAccount() {
        return salesTaxAccount;
    }

    public void setSalesTaxAccount(ChartOfAccount salesTaxAccount) {
        this.salesTaxAccount = salesTaxAccount;
    }

    public ChartOfAccount getPurchaseTaxAccount() {
        return purchaseTaxAccount;
    }

    public void setPurchaseTaxAccount(ChartOfAccount purchaseTaxAccount) {
        this.purchaseTaxAccount = purchaseTaxAccount;
    }

    public boolean isTrackSalesTaxSeparately() {
        return trackSalesTaxSeparately;
    }

    public void setTrackSalesTaxSeparately(boolean trackSalesTaxSeparately) {
        this.trackSalesTaxSeparately = trackSalesTaxSeparately;
    }

    public boolean isTrackPurchaseTaxSeparately() {
        return trackPurchaseTaxSeparately;
    }

    public void setTrackPurchaseTaxSeparately(boolean trackPurchaseTaxSeparately) {
        this.trackPurchaseTaxSeparately = trackPurchaseTaxSeparately;
    }

    public boolean isTaxOnOtherTaxes() {
        return taxOnOtherTaxes;
    }

    public void setTaxOnOtherTaxes(boolean taxOnOtherTaxes) {
        this.taxOnOtherTaxes = taxOnOtherTaxes;
    }

    public boolean isTaxIncludedOnSales() {
        return taxIncludedOnSales;
    }

    public void setTaxIncludedOnSales(boolean taxIncludedOnSales) {
        this.taxIncludedOnSales = taxIncludedOnSales;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isInactive() {
        return inactive;
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

