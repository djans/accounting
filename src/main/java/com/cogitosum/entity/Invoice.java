package com.cogitosum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
public class Invoice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String invoiceNumber;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    
    @Column(nullable = false)
    private LocalDate invoiceDate;
    
    @Column(nullable = false)
    private LocalDate dueDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;
    
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<LineItem> lineItems = new ArrayList<>();
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal gstAmount;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal hstAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal qstAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Column(length = 30)
    private String taxRegime;
    
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
        if (status == null) {
            status = InvoiceStatus.DRAFT;
        }
        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }
        if (qstAmount == null) {
            qstAmount = BigDecimal.ZERO;
        }
        if (gstAmount == null) {
            gstAmount = BigDecimal.ZERO;
        }
        if (hstAmount == null) {
            hstAmount = BigDecimal.ZERO;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public void calculateTotals(BigDecimal gstRate, BigDecimal hstRate, BigDecimal qstRate) {
        this.subtotal = lineItems.stream()
            .map(LineItem::calculateTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.gstAmount = this.subtotal.multiply(gstRate).setScale(2, java.math.RoundingMode.HALF_UP);
        this.hstAmount = this.subtotal.multiply(hstRate).setScale(2, java.math.RoundingMode.HALF_UP);
        this.qstAmount = this.subtotal.multiply(qstRate).setScale(2, java.math.RoundingMode.HALF_UP);
        this.totalAmount = this.subtotal.add(this.gstAmount).add(this.hstAmount).add(this.qstAmount);
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getInvoiceNumber() {
        return invoiceNumber;
    }
    
    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }
    
    public Customer getCustomer() {
        return customer;
    }
    
    public void setCustomer(Customer customer) {
        this.customer = customer;
    }
    
    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }
    
    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }
    
    public LocalDate getDueDate() {
        return dueDate;
    }
    
    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
    
    public InvoiceStatus getStatus() {
        return status;
    }
    
    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }
    
    public List<LineItem> getLineItems() {
        return lineItems;
    }
    
    public void setLineItems(List<LineItem> lineItems) {
        this.lineItems = lineItems;
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
    
    public BigDecimal getPaidAmount() {
        return paidAmount;
    }
    
    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
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
