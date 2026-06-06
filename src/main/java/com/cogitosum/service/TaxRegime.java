package com.cogitosum.service;

import java.math.BigDecimal;

public record TaxRegime(String code, String label, BigDecimal gstRate, BigDecimal hstRate, BigDecimal qstRate) {
}
