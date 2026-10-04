package com.cogitosum.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cheque_expenses")
public class ChequeExpense {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private WrittenCheque cheque;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) private ChartOfAccount account;
    @Column(length = 40) private String tax;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(length = 500) private String memo;
    @ManyToOne(fetch = FetchType.EAGER) private Customer customerJob;

    public Long getId() { return id; }
    public WrittenCheque getCheque() { return cheque; }
    public void setCheque(WrittenCheque value) { cheque = value; }
    public ChartOfAccount getAccount() { return account; }
    public void setAccount(ChartOfAccount value) { account = value; }
    public String getTax() { return tax; }
    public void setTax(String value) { tax = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getMemo() { return memo; }
    public void setMemo(String value) { memo = value; }
    public Customer getCustomerJob() { return customerJob; }
    public void setCustomerJob(Customer value) { customerJob = value; }
}
