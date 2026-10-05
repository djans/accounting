package com.cogitosum.dto;

import java.math.BigDecimal;

public record TaxReturnLineDTO(String type, String returnLine, BigDecimal amount) {
}
