package com.cogitosum.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "credit_notes", uniqueConstraints = @UniqueConstraint(
        name = "uk_credit_notes_company_number", columnNames = {"company_id", "credit_note_number"}))
public class CreditNote implements CompanyOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "credit_note_number", nullable = false)
    private String creditNoteNumber;

    @Column(name = "credit_note_date", nullable = false)
    private LocalDate creditNoteDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CreditNoteStatus status;

    @Column(nullable = false, length = 1000)
    private String reason;

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("lineNumber ASC")
    private List<CreditNoteLineItem> lineItems = new ArrayList<>();

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "gst_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal gstAmount = BigDecimal.ZERO;

    @Column(name = "hst_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal hstAmount = BigDecimal.ZERO;

    @Column(name = "qst_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal qstAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = CreditNoteStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public Company getCompany() {
        return company;
    }

    @Override
    public void setCompany(Company company) {
        this.company = company;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public String getCreditNoteNumber() {
        return creditNoteNumber;
    }

    public void setCreditNoteNumber(String creditNoteNumber) {
        this.creditNoteNumber = creditNoteNumber;
    }

    public LocalDate getCreditNoteDate() {
        return creditNoteDate;
    }

    public void setCreditNoteDate(LocalDate creditNoteDate) {
        this.creditNoteDate = creditNoteDate;
    }

    public CreditNoteStatus getStatus() {
        return status;
    }

    public void setStatus(CreditNoteStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<CreditNoteLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<CreditNoteLineItem> lineItems) {
        this.lineItems = lineItems == null ? new ArrayList<>() : lineItems;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getGstAmount() {
        return gstAmount;
    }

    public void setGstAmount(BigDecimal gstAmount) {
        this.gstAmount = gstAmount;
    }

    public BigDecimal getHstAmount() {
        return hstAmount;
    }

    public void setHstAmount(BigDecimal hstAmount) {
        this.hstAmount = hstAmount;
    }

    public BigDecimal getQstAmount() {
        return qstAmount;
    }

    public void setQstAmount(BigDecimal qstAmount) {
        this.qstAmount = qstAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
