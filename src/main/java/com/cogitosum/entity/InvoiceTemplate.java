package com.cogitosum.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoice_templates", uniqueConstraints = @UniqueConstraint(
    name = "uk_invoice_templates_company_language_name",
    columnNames = {"company_id", "language_tag", "name"}))
public class InvoiceTemplate implements CompanyOwned {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "language_tag", nullable = false, length = 35)
    private String languageTag;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Lob
    @Column(name = "template_data", nullable = false)
    @JsonIgnore
    private byte[] templateData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public String getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public void setLanguageTag(String languageTag) {
        this.languageTag = languageTag;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    @JsonIgnore
    public byte[] getTemplateData() {
        return templateData == null ? null : templateData.clone();
    }

    public void setTemplateData(byte[] templateData) {
        this.templateData = templateData == null ? null : templateData.clone();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
