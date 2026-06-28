package com.cogitosum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Ligne de facture d'achat — contrepartie de {@link LineItem}.
 * Chaque ligne impute un compte de charge ou d'actif précis ({@link #expenseAccount}),
 * débité lors de la comptabilisation.
 */
@Entity
@Table(name = "bill_line_items")
public class BillLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "expense_account_id")
    private ChartOfAccount expenseAccount;

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

    public Bill getBill() {
        return bill;
    }

    public void setBill(Bill bill) {
        this.bill = bill;
    }

    public ChartOfAccount getExpenseAccount() {
        return expenseAccount;
    }

    public void setExpenseAccount(ChartOfAccount expenseAccount) {
        this.expenseAccount = expenseAccount;
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
