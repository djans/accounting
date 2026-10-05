package com.cogitosum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tax_return_row_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "uk_tax_return_snapshot_period_order",
                columnNames = {"period_id", "display_order"}))
public class TaxReturnRowSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private TaxFilingPeriod period;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "description_key", nullable = false, length = 200)
    private String descriptionKey;

    @Column(length = 20)
    private String line;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false)
    private boolean total;

    @Column(nullable = false)
    private boolean unmapped;

    public Long getId() { return id; }
    public TaxFilingPeriod getPeriod() { return period; }
    public void setPeriod(TaxFilingPeriod period) { this.period = period; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public String getDescriptionKey() { return descriptionKey; }
    public void setDescriptionKey(String descriptionKey) { this.descriptionKey = descriptionKey; }
    public String getLine() { return line; }
    public void setLine(String line) { this.line = line; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public boolean isTotal() { return total; }
    public void setTotal(boolean total) { this.total = total; }
    public boolean isUnmapped() { return unmapped; }
    public void setUnmapped(boolean unmapped) { this.unmapped = unmapped; }
}
