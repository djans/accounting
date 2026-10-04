package com.cogitosum.migration.v1;

import java.util.List;
import java.util.Map;

/**
 * The result of parsing and validating an initial-data migration package.
 */
public final class MigrationPreview {

    private final String confirmationToken;
    private final Map<String, Integer> rowCounts;
    private final List<MigrationValidationError> errors;

    public MigrationPreview(String confirmationToken, Map<String, Integer> rowCounts,
                            List<MigrationValidationError> errors) {
        this.confirmationToken = confirmationToken;
        this.rowCounts = Map.copyOf(rowCounts);
        this.errors = List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty() && confirmationToken != null;
    }

    public String getConfirmationToken() {
        return confirmationToken;
    }

    public Map<String, Integer> getRowCounts() {
        return rowCounts;
    }

    public List<MigrationValidationError> getErrors() {
        return errors;
    }
}
