package com.cogitosum.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_final_pdfs")
public class InvoiceFinalPdf {

    @Id
    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Lob
    @Column(name = "pdf_data", nullable = false)
    private byte[] pdfData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected InvoiceFinalPdf() {
    }

    public InvoiceFinalPdf(Long invoiceId, byte[] pdfData) {
        this.invoiceId = invoiceId;
        this.pdfData = pdfData == null ? null : pdfData.clone();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    @JsonIgnore
    public byte[] getPdfData() {
        return pdfData == null ? null : pdfData.clone();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
