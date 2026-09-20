package com.cogitosum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fournisseur — contrepartie d'achat de {@link Customer}.
 * Les numéros d'entreprise / TPS / TVQ servent à justifier les crédits de taxe sur intrants (CTI/RTI).
 */
@Entity
@Table(name = "vendors")
public class Vendor {

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
    @Column(length = 1000) private String shipFromAddress;
    private String accountNumber;
    private String paymentTerms;
    private String billingRateLevel;
    private String printOnChequeAs;
    private BigDecimal creditLimit;
    private boolean taxAgency;
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
    private boolean taxIncludedOnExpenses;
    @ManyToOne(fetch = FetchType.EAGER) private ChartOfAccount defaultExpenseAccount1;
    @ManyToOne(fetch = FetchType.EAGER) private ChartOfAccount defaultExpenseAccount2;
    @ManyToOne(fetch = FetchType.EAGER) private ChartOfAccount defaultExpenseAccount3;
    private String vendorType;
    @Column(length = 2000) private String customFields;
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

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String value) { companyName = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String value) { firstName = value; }
    public String getMiddleInitial() { return middleInitial; }
    public void setMiddleInitial(String value) { middleInitial = value; }
    public String getLastName() { return lastName; }
    public void setLastName(String value) { lastName = value; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String value) { jobTitle = value; }
    public String getMainPhone() { return mainPhone; }
    public void setMainPhone(String value) { mainPhone = value; }
    public String getWorkPhone() { return workPhone; }
    public void setWorkPhone(String value) { workPhone = value; }
    public String getMobilePhone() { return mobilePhone; }
    public void setMobilePhone(String value) { mobilePhone = value; }
    public String getFax() { return fax; }
    public void setFax(String value) { fax = value; }
    public String getWebsite() { return website; }
    public void setWebsite(String value) { website = value; }
    public String getSecondaryEmail() { return secondaryEmail; }
    public void setSecondaryEmail(String value) { secondaryEmail = value; }
    public String getCcEmail() { return ccEmail; }
    public void setCcEmail(String value) { ccEmail = value; }
    public String getShipFromAddress() { return shipFromAddress; }
    public void setShipFromAddress(String value) { shipFromAddress = value; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String value) { accountNumber = value; }
    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String value) { paymentTerms = value; }
    public String getBillingRateLevel() { return billingRateLevel; }
    public void setBillingRateLevel(String value) { billingRateLevel = value; }
    public String getPrintOnChequeAs() { return printOnChequeAs; }
    public void setPrintOnChequeAs(String value) { printOnChequeAs = value; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal value) { creditLimit = value; }
    public boolean isTaxAgency() { return taxAgency; }
    public void setTaxAgency(boolean value) { taxAgency = value; }
    public String getTaxReturnType() { return taxReturnType; }
    public void setTaxReturnType(String value) { taxReturnType = value; }
    public String getTaxReportingPeriod() { return taxReportingPeriod; }
    public void setTaxReportingPeriod(String value) { taxReportingPeriod = value; }
    public String getTaxPeriodEnding() { return taxPeriodEnding; }
    public void setTaxPeriodEnding(String value) { taxPeriodEnding = value; }
    public String getTaxLabel() { return taxLabel; }
    public void setTaxLabel(String value) { taxLabel = value; }
    public String getSalesTaxRegistrationNumber() { return salesTaxRegistrationNumber; }
    public void setSalesTaxRegistrationNumber(String value) { salesTaxRegistrationNumber = value; }
    public ChartOfAccount getSalesTaxAccount() { return salesTaxAccount; }
    public void setSalesTaxAccount(ChartOfAccount value) { salesTaxAccount = value; }
    public ChartOfAccount getPurchaseTaxAccount() { return purchaseTaxAccount; }
    public void setPurchaseTaxAccount(ChartOfAccount value) { purchaseTaxAccount = value; }
    public boolean isTrackSalesTaxSeparately() { return trackSalesTaxSeparately; }
    public void setTrackSalesTaxSeparately(boolean value) { trackSalesTaxSeparately = value; }
    public boolean isTrackPurchaseTaxSeparately() { return trackPurchaseTaxSeparately; }
    public void setTrackPurchaseTaxSeparately(boolean value) { trackPurchaseTaxSeparately = value; }
    public boolean isTaxOnOtherTaxes() { return taxOnOtherTaxes; }
    public void setTaxOnOtherTaxes(boolean value) { taxOnOtherTaxes = value; }
    public boolean isTaxIncludedOnExpenses() { return taxIncludedOnExpenses; }
    public void setTaxIncludedOnExpenses(boolean value) { taxIncludedOnExpenses = value; }
    public ChartOfAccount getDefaultExpenseAccount1() { return defaultExpenseAccount1; }
    public void setDefaultExpenseAccount1(ChartOfAccount value) { defaultExpenseAccount1 = value; }
    public ChartOfAccount getDefaultExpenseAccount2() { return defaultExpenseAccount2; }
    public void setDefaultExpenseAccount2(ChartOfAccount value) { defaultExpenseAccount2 = value; }
    public ChartOfAccount getDefaultExpenseAccount3() { return defaultExpenseAccount3; }
    public void setDefaultExpenseAccount3(ChartOfAccount value) { defaultExpenseAccount3 = value; }
    public String getVendorType() { return vendorType; }
    public void setVendorType(String value) { vendorType = value; }
    public String getCustomFields() { return customFields; }
    public void setCustomFields(String value) { customFields = value; }
    public boolean isInactive() { return inactive; }
    public void setInactive(boolean value) { inactive = value; }

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
