package com.cogitosum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "credit_note_line_items")
public class CreditNoteLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false)
    private CreditNote creditNote;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @PrePersist
    @PreUpdate
    protected void recalculateTotal() {
        total = calculateTotal();
    }

    public BigDecimal calculateTotal() {
        return quantity.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public CreditNote getCreditNote() {
        return creditNote;
    }

    public void setCreditNote(CreditNote creditNote) {
        this.creditNote = creditNote;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
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
        return total == null ? calculateTotal() : total;
    }
}
