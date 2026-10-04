package com.cogitosum.migration.v1;

/**
 * A location-aware, safe-to-display validation finding for a migration package.
 * Values from the uploaded package are deliberately not included in messages.
 */
public record MigrationValidationError(String fileName, int rowNumber, String message) {
}
