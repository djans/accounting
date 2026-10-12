package com.cogitosum.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibreOfficePdfConverterTest {

    @Test
    void reportsHowToConfigureLibreOfficeWhenTheConfiguredPathIsMissing() {
        Path missingExecutable = Path.of(System.getProperty("java.io.tmpdir"),
                "missing-libreoffice-" + UUID.randomUUID(), "soffice.exe");
        LibreOfficePdfConverter converter = new LibreOfficePdfConverter(missingExecutable.toString());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> converter.convertToPdf(new byte[]{1}));

        assertTrue(exception.getMessage().contains("APP_INVOICE_PDF_LIBREOFFICE_COMMAND"));
        assertTrue(exception.getMessage().contains(missingExecutable.toString()));
    }
}
