package com.cogitosum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "written_cheques", uniqueConstraints = @UniqueConstraint(
        name = "uk_written_cheques_company_number", columnNames = {"company_id", "cheque_number"}))
public class WrittenCheque implements CompanyOwned {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;
    @Column(nullable = false) private String chequeNumber;
    @Column(nullable = false) private LocalDate chequeDate;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) private Vendor vendor;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) private ChartOfAccount bankAccount;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(length = 500) private String memo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WrittenChequeStatus status = WrittenChequeStatus.DRAFT;
    @Column(name = "voided_at")
    private LocalDateTime voidedAt;
    @Column(name = "void_reason", length = 500)
    private String voidReason;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reissue_of_id")
    private WrittenCheque reissueOf;
    @OneToMany(mappedBy = "cheque", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ChequeExpense> expenses = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = WrittenChequeStatus.DRAFT;
        }
    }

    public Long getId() { return id; }
    @Override
    public Company getCompany() { return company; }
    @Override
    public void setCompany(Company value) { company = value; }
    public String getChequeNumber() { return chequeNumber; }
    public void setChequeNumber(String value) { chequeNumber = value; }
    public LocalDate getChequeDate() { return chequeDate; }
    public void setChequeDate(LocalDate value) { chequeDate = value; }
    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor value) { vendor = value; }
    public ChartOfAccount getBankAccount() { return bankAccount; }
    public void setBankAccount(ChartOfAccount value) { bankAccount = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getMemo() { return memo; }
    public void setMemo(String value) { memo = value; }
    public WrittenChequeStatus getStatus() { return status; }
    public void setStatus(WrittenChequeStatus value) { status = value; }
    public LocalDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(LocalDateTime value) { voidedAt = value; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String value) { voidReason = value; }
    public WrittenCheque getReissueOf() { return reissueOf; }
    public void setReissueOf(WrittenCheque value) { reissueOf = value; }
    public List<ChequeExpense> getExpenses() { return expenses; }
    public void setExpenses(List<ChequeExpense> value) { expenses = value; }
}
