package com.cogitosum.dto;

import java.time.LocalDate;
import java.util.List;

public class GeneralJournalDTO {
    private Long id;
    private String journalNumber;
    private LocalDate journalDate;
    private String narrative;
    private String reference;
    private String status;
    private List<JournalEntryDTO> entries;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<JournalEntryDTO> getEntries() {
        return entries;
    }

    public void setEntries(List<JournalEntryDTO> entries) {
        this.entries = entries;
    }
}

