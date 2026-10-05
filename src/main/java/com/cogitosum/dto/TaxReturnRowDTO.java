package com.cogitosum.dto;

import java.math.BigDecimal;

public record TaxReturnRowDTO(
        String descriptionKey,
        String line,
        BigDecimal amount,
        BigDecimal balance,
        boolean total,
        boolean unmapped) {
}
