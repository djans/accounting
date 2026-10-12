package com.cogitosum.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_categorization_rules")
public class BankCategorizationRule implements CompanyOwned {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "match_text", nullable = false, length = 255)
    private String matchText;

    @Column(nullable = false)
    private int priority;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_account_id", nullable = false)
    private ChartOfAccount targetAccount;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    @Override public Company getCompany() { return company; }
    @Override public void setCompany(Company company) { this.company = company; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMatchText() { return matchText; }
    public void setMatchText(String matchText) { this.matchText = matchText; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public ChartOfAccount getTargetAccount() { return targetAccount; }
    public void setTargetAccount(ChartOfAccount targetAccount) { this.targetAccount = targetAccount; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
