package com.cogitosum.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class LibreOfficePdfConverter {

    private static final Duration CONVERSION_TIMEOUT = Duration.ofSeconds(45);
    private static final String MISSING_EXECUTABLE_MESSAGE =
            "LibreOffice Writer is required to generate invoice PDFs. Install LibreOffice or set "
                    + "APP_INVOICE_PDF_LIBREOFFICE_COMMAND to the soffice executable path.";

    private final String configuredExecutable;

    public LibreOfficePdfConverter(
            @Value("${APP_INVOICE_PDF_LIBREOFFICE_COMMAND:${app.invoice.pdf.libreoffice-command:}}")
            String executable) {
        this.configuredExecutable = executable;
    }

    public byte[] convertToPdf(byte[] docx) {
        Path workspace;
        try {
            workspace = Files.createTempDirectory("accounting-invoice-");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create a temporary invoice-rendering directory", exception);
        }

        try {
            return convert(workspace, docx);
        } finally {
            deleteWorkspace(workspace);
        }
    }

    private byte[] convert(Path workspace, byte[] docx) {
        Path input = workspace.resolve("invoice.docx");
        Path output = workspace.resolve("invoice.pdf");
        Path log = workspace.resolve("libreoffice.log");
        Path profile = workspace.resolve("profile");
        Process process = null;

        try {
            Files.write(input, docx);
            process = new ProcessBuilder(
                    resolveExecutable(),
                    "--headless",
                    "--nologo",
                    "--nodefault",
                    "--nofirststartwizard",
                    "-env:UserInstallation=" + profile.toUri(),
                    "--convert-to", "pdf",
                    "--outdir", workspace.toString(),
                    input.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            if (!process.waitFor(CONVERSION_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IllegalStateException("Invoice PDF conversion exceeded the time limit");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output) || Files.size(output) == 0) {
                String details = Files.exists(log) ? Files.readString(log) : "";
                throw new IllegalStateException(
                        "LibreOffice could not convert the invoice template to PDF (exit "
                                + process.exitValue() + "): " + details);
            }
            return Files.readAllBytes(output);
        } catch (InterruptedException exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Invoice PDF conversion was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not convert the invoice template to PDF", exception);
        }
    }

    private String resolveExecutable() {
        if (configuredExecutable != null && !configuredExecutable.isBlank()) {
            String configured = configuredExecutable.trim();
            Path configuredPath = Path.of(configured);
            if (configuredPath.isAbsolute() || configured.contains("\\") || configured.contains("/")) {
                if (Files.isRegularFile(configuredPath)) {
                    return configuredPath.toString();
                }
                throw new IllegalStateException(MISSING_EXECUTABLE_MESSAGE + " Configured path was not found: "
                        + configured);
            }
            return findOnPath(List.of(configured)).orElseThrow(
                    () -> new IllegalStateException(MISSING_EXECUTABLE_MESSAGE + " Command was not found: "
                            + configured));
        }

        if (isWindows()) {
            for (String variable : List.of("ProgramFiles", "ProgramFiles(x86)")) {
                String programFiles = System.getenv(variable);
                if (programFiles != null && !programFiles.isBlank()) {
                    Path standardPath = Path.of(programFiles, "LibreOffice", "program", "soffice.exe");
                    if (Files.isRegularFile(standardPath)) {
                        return standardPath.toString();
                    }
                }
            }
            return findOnPath(List.of("soffice.exe", "soffice", "libreoffice.exe", "libreoffice"))
                    .orElseThrow(() -> new IllegalStateException(MISSING_EXECUTABLE_MESSAGE));
        }
        return findOnPath(List.of("libreoffice", "soffice"))
                .orElseThrow(() -> new IllegalStateException(MISSING_EXECUTABLE_MESSAGE));
    }

    private java.util.Optional<String> findOnPath(List<String> candidates) {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) {
            return java.util.Optional.empty();
        }
        boolean windows = isWindows();
        for (String directory : pathValue.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (directory.isBlank()) {
                continue;
            }
            for (String candidate : candidates) {
                Path candidatePath = Path.of(directory).resolve(candidate);
                if (Files.isRegularFile(candidatePath) && (windows || Files.isExecutable(candidatePath))) {
                    return java.util.Optional.of(candidatePath.toString());
                }
            }
        }
        return java.util.Optional.empty();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private void deleteWorkspace(Path workspace) {
        try (Stream<Path> paths = Files.walk(workspace)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not clean up temporary invoice-rendering files", exception);
        }
    }
}
