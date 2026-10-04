package com.cogitosum.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TaxContributionDTO {
    private Long id;
    private String type; // "INVOICE", "BILL", or "JOURNAL"
    private String number;
    private LocalDate date;
    private String entityName; // Customer or Vendor name
    private BigDecimal amount;

    public TaxContributionDTO() {}

    public TaxContributionDTO(Long id, String type, String number, LocalDate date, String entityName, BigDecimal amount) {
        this.id = id;
        this.type = type;
        this.number = number;
        this.date = date;
        this.entityName = entityName;
        this.amount = amount;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
