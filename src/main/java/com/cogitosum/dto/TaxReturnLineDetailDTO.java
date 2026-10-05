package com.cogitosum.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxReturnLineDetailDTO(
        String sourceType,
        Long sourceId,
        LocalDate date,
        String documentNumber,
        String description,
        String calculationKey,
        BigDecimal amount,
        BigDecimal taxableAmount,
        BigDecimal rate) {

    public TaxReturnLineDetailDTO(
            String sourceType,
            Long sourceId,
            LocalDate date,
            String documentNumber,
            String description,
            String calculationKey,
            BigDecimal amount) {
        this(sourceType, sourceId, date, documentNumber, description, calculationKey, amount, null, null);
    }
}
