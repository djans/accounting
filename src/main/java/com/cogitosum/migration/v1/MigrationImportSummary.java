package com.cogitosum.migration.v1;

import java.util.Map;

/**
 * Counts returned after a committed v1 migration.
 */
public record MigrationImportSummary(Map<String, Integer> rowCounts) {

    public int totalRows() {
        return rowCounts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
