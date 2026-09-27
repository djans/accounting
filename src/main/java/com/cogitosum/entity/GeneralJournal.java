package com.cogitosum.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "general_journals", uniqueConstraints = @UniqueConstraint(
        name = "uk_general_journals_company_number", columnNames = {"company_id", "journal_number"}))
public class GeneralJournal implements CompanyOwned {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;
    
    @Column(nullable = false)
    private String journalNumber;
    
    @Column(nullable = false)
    private LocalDate journalDate;
    
    @Column(nullable = false)
    private String narrative;
    
    @Column(length = 500)
    private String reference;   // Links to Invoice/Payment
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JournalStatus status;
    
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<JournalEntry> entries = new ArrayList<>();

    private LocalDateTime postedDate;

    @Column(length = 500)
    private String postedBy;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = JournalStatus.DRAFT;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
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
    
    public String getJournalNumber() {
        return journalNumber;
    }
    
    public void setJournalNumber(String journalNumber) {
        this.journalNumber = journalNumber;
    }
    
    public LocalDate getJournalDate() {
        return journalDate;
    }
    
    public void setJournalDate(LocalDate journalDate) {
        this.journalDate = journalDate;
    }
    
    public String getNarrative() {
        return narrative;
    }
    
    public void setNarrative(String narrative) {
        this.narrative = narrative;
    }
    
    public String getReference() {
        return reference;
    }
    
    public void setReference(String reference) {
        this.reference = reference;
    }
    
    public JournalStatus getStatus() {
        return status;
    }
    
    public void setStatus(JournalStatus status) {
        this.status = status;
    }
    
    public List<JournalEntry> getEntries() {
        return entries;
    }
    
    public void setEntries(List<JournalEntry> entries) {
        this.entries = entries;
    }
    
    public LocalDateTime getPostedDate() {
        return postedDate;
    }
    
    public void setPostedDate(LocalDateTime postedDate) {
        this.postedDate = postedDate;
    }
    
    public String getPostedBy() {
        return postedBy;
    }
    
    public void setPostedBy(String postedBy) {
        this.postedBy = postedBy;
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
