package com.cogitosum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "line_items")
public class LineItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;
    
    @Column(nullable = false)
    private String description;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal quantity;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;
    
    @PrePersist
    @PreUpdate
    protected void recalculateTotal() {
        this.total = calculateTotal();
    }

    public BigDecimal calculateTotal() {
        return this.quantity.multiply(this.unitPrice);
    }


    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Invoice getInvoice() {
        return invoice;
    }
    
    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public BigDecimal getQuantity() {
        return quantity;
    }
    
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
    
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
    
    public BigDecimal getTotal() {
        return total;
    }
    
    public void setTotal(BigDecimal total) {
        this.total = total;
    }
}

