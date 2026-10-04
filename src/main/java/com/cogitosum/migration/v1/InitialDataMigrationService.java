package com.cogitosum.migration.v1;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.Bill;
import com.cogitosum.entity.BillLineItem;
import com.cogitosum.entity.BillStatus;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.Company;
import com.cogitosum.entity.Customer;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.entity.LineItem;
import com.cogitosum.entity.Vendor;
import com.cogitosum.repository.BillRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.CustomerRepository;
import com.cogitosum.repository.GeneralJournalRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.JournalEntryRepository;
import com.cogitosum.repository.VendorRepository;
import com.cogitosum.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imports the narrowly defined, versioned initial-data package. It intentionally
 * uses JPA entities and repositories, never the table-oriented backup importer.
 */
@Service
public class InitialDataMigrationService {

    public static final String FORMAT = "cogitosum-accounting-migration";
    public static final int VERSION = 1;

    private static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 10;
    private static final int MAX_ERRORS = 100;
    private static final int MAX_PENDING_IMPORTS = 3;
    private static final int MAX_TEXT_CELL_CHARS = 10_000;

    private static final List<String> REQUIRED_FILES = List.of(
        "manifest.json",
        "accounts.csv",
        "customers.csv",
        "vendors.csv",
        "invoices.csv",
        "invoice_lines.csv",
        "bills.csv",
        "bill_lines.csv",
        "journal_entries.csv",
        "journal_lines.csv"
    );

    private static final Map<String, List<String>> HEADERS = Map.ofEntries(
        Map.entry("accounts.csv", List.of(
            "source_id", "account_number", "account_name", "account_type", "description", "is_active")),
        Map.entry("customers.csv", List.of(
            "source_id", "name", "email", "business_name", "address", "city", "province", "postal_code",
            "country", "business_number", "gst_number")),
        Map.entry("vendors.csv", List.of(
            "source_id", "name", "email", "business_name", "address", "city", "province", "postal_code",
            "country", "business_number", "gst_number", "qst_number")),
        Map.entry("invoices.csv", List.of(
            "source_id", "invoice_number", "customer_source_id", "invoice_date", "due_date", "status",
            "subtotal", "gst_amount", "hst_amount", "qst_amount", "total_amount", "paid_amount",
            "tax_regime", "notes")),
        Map.entry("invoice_lines.csv", List.of(
            "source_id", "invoice_source_id", "line_number", "description", "quantity", "unit_price", "total")),
        Map.entry("bills.csv", List.of(
            "source_id", "bill_number", "vendor_source_id", "bill_date", "due_date", "status",
            "subtotal", "gst_amount", "hst_amount", "qst_amount", "total_amount", "paid_amount", "notes")),
        Map.entry("bill_lines.csv", List.of(
            "source_id", "bill_source_id", "line_number", "expense_account_source_id", "description",
            "quantity", "unit_price", "total")),
        Map.entry("journal_entries.csv", List.of(
            "source_id", "journal_number", "journal_date", "narrative", "reference", "status",
            "posted_date", "posted_by")),
        Map.entry("journal_lines.csv", List.of(
            "source_id", "journal_source_id", "line_number", "account_source_id", "debit", "credit",
            "cleared", "description"))
    );

    private final ObjectMapper objectMapper;
    private final ChartOfAccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final VendorRepository vendorRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillRepository billRepository;
    private final GeneralJournalRepository journalRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final GeneralLedgerRepository ledgerRepository;
    private final EntityManager entityManager;
    private final UserAccountRepository userAccountRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PendingPlan> pendingPlans = new ConcurrentHashMap<>();
    private final Object importMonitor = new Object();

    public InitialDataMigrationService(
            ObjectMapper objectMapper,
            ChartOfAccountRepository accountRepository,
            CustomerRepository customerRepository,
            VendorRepository vendorRepository,
            InvoiceRepository invoiceRepository,
            BillRepository billRepository,
            GeneralJournalRepository journalRepository,
            JournalEntryRepository journalEntryRepository,
            GeneralLedgerRepository ledgerRepository,
            EntityManager entityManager,
            UserAccountRepository userAccountRepository) {
        this.objectMapper = objectMapper;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.vendorRepository = vendorRepository;
        this.invoiceRepository = invoiceRepository;
        this.billRepository = billRepository;
        this.journalRepository = journalRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.ledgerRepository = ledgerRepository;
        this.entityManager = entityManager;
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * Parses a package and retains an immutable, short-lived plan for a later,
     * separate confirmation request. No database rows are written here.
     */
    public MigrationPreview preview(byte[] packageBytes, String requestedBy) {
        Validation validation = new Validation();
        MigrationPlan plan = parse(packageBytes, validation);
        if (validation.hasErrors()) {
            return new MigrationPreview(null, plan.rowCounts, validation.errors);
        }
        if (!isMigrationDomainEmpty(requireCompany(requestedBy))) {
            validation.error("manifest.json", 0,
                "The target already contains accounting data; initial migration is allowed only into an empty migration domain.");
            return new MigrationPreview(null, plan.rowCounts, validation.errors);
        }

        pruneExpiredPlans();
        if (pendingPlans.size() >= MAX_PENDING_IMPORTS) {
            validation.error("manifest.json", 0, "Too many pending migration previews exist. Try again shortly.");
            return new MigrationPreview(null, plan.rowCounts, validation.errors);
        }

        String token = newToken();
        pendingPlans.put(token, new PendingPlan(requestedBy, Instant.now().plusSeconds(15 * 60), plan));
        return new MigrationPreview(token, plan.rowCounts, List.of());
    }

    /**
     * Performs the only write path. The transaction, final empty-domain check and
     * JVM import monitor prevent a preview from turning into a partial or merged import.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public MigrationImportSummary confirmAndImport(String token, String requestedBy) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("The migration preview has expired or is not available. Validate the package again.");
        }
        PendingPlan pendingPlan = pendingPlans.get(token);
        if (pendingPlan == null || pendingPlan.expiresAt.isBefore(Instant.now())
                || !Objects.equals(pendingPlan.requestedBy, requestedBy)) {
            pendingPlans.remove(token);
            throw new IllegalArgumentException("The migration preview has expired or is not available. Validate the package again.");
        }

        synchronized (importMonitor) {
            Company company = requireCompany(requestedBy);
            if (!isMigrationDomainEmpty(company)) {
                throw new IllegalStateException(
                    "The target now contains accounting data. No migration data was imported.");
            }
            importPlan(pendingPlan.plan, company);
            entityManager.flush();
            pendingPlans.remove(token);
            return new MigrationImportSummary(pendingPlan.plan.rowCounts);
        }
    }

    private MigrationPlan parse(byte[] packageBytes, Validation validation) {
        MigrationPlan empty = MigrationPlan.empty();
        if (packageBytes == null || packageBytes.length == 0) {
            validation.error("manifest.json", 0, "Select a non-empty migration package.");
            return empty;
        }
        if (packageBytes.length > MAX_UPLOAD_BYTES) {
            validation.error("manifest.json", 0, "The migration package exceeds the 20 MiB limit.");
            return empty;
        }

        Map<String, byte[]> entries = readZip(packageBytes, validation);
        if (entries.isEmpty() || validation.hasErrors()) {
            return empty;
        }
        validateManifest(entries.get("manifest.json"), validation);
        if (validation.hasErrors()) {
            return empty;
        }

        Map<String, List<CsvRecord>> csv = new LinkedHashMap<>();
        Map<String, Integer> rowCounts = new LinkedHashMap<>();
        for (String filename : HEADERS.keySet()) {
            List<CsvRecord> rows = parseCanonicalCsv(filename, entries.get(filename), validation);
            csv.put(filename, rows);
            rowCounts.put(filename, rows.size());
        }
        if (validation.hasErrors()) {
            return MigrationPlan.withCounts(rowCounts);
        }

        MigrationPlan plan = toPlan(csv, rowCounts, validation);
        validateRelationships(plan, validation);
        return plan;
    }

    private Map<String, byte[]> readZip(byte[] packageBytes, Validation validation) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        int[] totalUncompressed = {0};
        int entryCount = 0;

        try (ZipInputStream input = new ZipInputStream(
                new ByteArrayInputStream(packageBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (++entryCount > MAX_ZIP_ENTRIES) {
                    validation.error("manifest.json", 0, "The package contains too many ZIP entries.");
                    break;
                }
                String name = entry.getName();
                if (entry.isDirectory() || name == null || !REQUIRED_FILES.contains(name)) {
                    validation.error("manifest.json", 0, "The package contains an unsupported ZIP entry.");
                }
                if (!seen.add(name == null ? "" : name)) {
                    validation.error("manifest.json", 0, "The package contains a duplicate ZIP entry.");
                }
                byte[] content = readEntry(input, totalUncompressed, validation, name == null ? "manifest.json" : name);
                if (name != null && REQUIRED_FILES.contains(name) && !entries.containsKey(name)) {
                    entries.put(name, content);
                }
                input.closeEntry();
            }
        } catch (IOException ex) {
            validation.error("manifest.json", 0, "The uploaded file is not a readable UTF-8 ZIP package.");
            return Map.of();
        }

        for (String required : REQUIRED_FILES) {
            if (!entries.containsKey(required)) {
                validation.error(required, 0, "Required file is missing from the package.");
            }
        }
        return entries;
    }

    private byte[] readEntry(ZipInputStream input, int[] totalUncompressed, Validation validation, String filename)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            totalUncompressed[0] += read;
            if (output.size() + read > MAX_ENTRY_BYTES || totalUncompressed[0] > MAX_UPLOAD_BYTES) {
                validation.error(filename, 0, "The ZIP package expands beyond the permitted size.");
                throw new IOException("ZIP entry limit exceeded");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void validateManifest(byte[] manifestBytes, Validation validation) {
        try {
            JsonNode manifest = objectMapper.readTree(manifestBytes);
            if (manifest == null || !manifest.isObject()
                    || manifest.size() != 2
                    || !manifest.path("format").isTextual()
                    || !manifest.path("version").isInt()
                    || !FORMAT.equals(manifest.path("format").asText())
                    || manifest.path("version").asInt() != VERSION) {
                validation.error("manifest.json", 1, "Manifest must contain exactly the supported format and version.");
            }
        } catch (Exception ex) {
            validation.error("manifest.json", 1, "Manifest is not valid UTF-8 JSON.");
        }
    }

    private List<CsvRecord> parseCanonicalCsv(String filename, byte[] bytes, Validation validation) {
        String csv = decodeUtf8(bytes, filename, validation);
        if (csv == null) {
            return List.of();
        }
        List<CsvRow> parsedRows = parseCsvRows(filename, csv, validation);
        if (parsedRows.isEmpty()) {
            validation.error(filename, 1, "CSV file must contain the canonical header row.");
            return List.of();
        }
        List<String> expectedHeader = HEADERS.get(filename);
        CsvRow header = parsedRows.get(0);
        if (!header.values.equals(expectedHeader)) {
            validation.error(filename, header.lineNumber,
                "Header must exactly match the required canonical header order.");
            return List.of();
        }

        List<CsvRecord> records = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < parsedRows.size(); rowIndex++) {
            CsvRow row = parsedRows.get(rowIndex);
            if (row.values.size() != expectedHeader.size()) {
                validation.error(filename, row.lineNumber,
                    "CSV row has the wrong number of columns for its canonical header.");
                continue;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            for (int index = 0; index < expectedHeader.size(); index++) {
                String cell = row.values.get(index);
                if (cell.length() > MAX_TEXT_CELL_CHARS) {
                    validation.error(filename, row.lineNumber, "A CSV field exceeds the permitted length.");
                }
                fields.put(expectedHeader.get(index), cell);
            }
            records.add(new CsvRecord(filename, row.lineNumber, fields));
        }
        return records;
    }

    private String decodeUtf8(byte[] bytes, String filename, Validation validation) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            String decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString();
            if (decoded.indexOf('\u0000') >= 0) {
                validation.error(filename, 0, "CSV content contains an unsupported control character.");
                return null;
            }
            return decoded.startsWith("\uFEFF") ? decoded.substring(1) : decoded;
        } catch (CharacterCodingException ex) {
            validation.error(filename, 0, "CSV file is not valid UTF-8.");
            return null;
        }
    }

    private List<CsvRow> parseCsvRows(String filename, String csv, Validation validation) {
        List<CsvRow> rows = new ArrayList<>();
        List<String> values = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        boolean hasRecordData = false;
        int line = 1;
        int recordLine = 1;

        for (int index = 0; index < csv.length(); index++) {
            char ch = csv.charAt(index);
            if (quoted) {
                if (ch == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        afterQuote = true;
                    }
                } else if (ch == '\r' || ch == '\n') {
                    if (ch == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') {
                        index++;
                    }
                    field.append('\n');
                    line++;
                } else {
                    field.append(ch);
                }
                continue;
            }

            if (afterQuote) {
                if (ch == ',') {
                    values.add(field.toString());
                    field.setLength(0);
                    afterQuote = false;
                    hasRecordData = true;
                } else if (ch == '\r' || ch == '\n') {
                    if (ch == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') {
                        index++;
                    }
                    values.add(field.toString());
                    rows.add(new CsvRow(recordLine, List.copyOf(values)));
                    values.clear();
                    field.setLength(0);
                    afterQuote = false;
                    hasRecordData = false;
                    line++;
                    recordLine = line;
                } else {
                    validation.error(filename, line, "Unexpected content after a quoted CSV field.");
                    return rows;
                }
                continue;
            }

            if (ch == '"') {
                if (field.isEmpty()) {
                    quoted = true;
                    hasRecordData = true;
                } else {
                    validation.error(filename, line, "Quote characters must begin a CSV field.");
                    return rows;
                }
            } else if (ch == ',') {
                values.add(field.toString());
                field.setLength(0);
                hasRecordData = true;
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') {
                    index++;
                }
                values.add(field.toString());
                rows.add(new CsvRow(recordLine, List.copyOf(values)));
                values.clear();
                field.setLength(0);
                hasRecordData = false;
                line++;
                recordLine = line;
            } else {
                field.append(ch);
                hasRecordData = true;
            }
        }

        if (quoted) {
            validation.error(filename, recordLine, "CSV file has an unterminated quoted field.");
        } else if (hasRecordData || !values.isEmpty() || !field.isEmpty()) {
            values.add(field.toString());
            rows.add(new CsvRow(recordLine, List.copyOf(values)));
        }
        return rows;
    }

    private MigrationPlan toPlan(Map<String, List<CsvRecord>> csv, Map<String, Integer> rowCounts,
                                 Validation validation) {
        List<AccountRow> accounts = new ArrayList<>();
        Set<String> accountSources = new HashSet<>();
        Set<String> accountNumbers = new HashSet<>();
        for (CsvRecord row : csv.get("accounts.csv")) {
            int before = validation.size();
            String source = sourceId(row, "source_id", accountSources, validation);
            String accountNumber = uniqueText(row, "account_number", 255, accountNumbers, "account number", validation);
            String accountName = requiredText(row, "account_name", 255, validation);
            AccountType accountType = enumValue(row, "account_type", AccountType.class, validation);
            String description = requiredText(row, "description", 255, validation);
            Boolean active = boolValue(row, "is_active", validation);
            if (validation.size() == before) {
                accounts.add(new AccountRow(row, source, accountNumber, accountName, accountType, description, active));
            }
        }

        List<CustomerRow> customers = new ArrayList<>();
        Set<String> customerSources = new HashSet<>();
        Set<String> customerEmails = new HashSet<>();
        for (CsvRecord row : csv.get("customers.csv")) {
            int before = validation.size();
            String source = sourceId(row, "source_id", customerSources, validation);
            String name = requiredText(row, "name", 255, validation);
            String email = uniqueEmail(row, "email", customerEmails, validation);
            String businessName = requiredText(row, "business_name", 255, validation);
            String address = requiredText(row, "address", 255, validation);
            String city = requiredText(row, "city", 255, validation);
            String province = requiredText(row, "province", 255, validation);
            String postalCode = requiredText(row, "postal_code", 255, validation);
            String country = requiredText(row, "country", 255, validation);
            String businessNumber = optionalText(row, "business_number", 255, validation);
            String gstNumber = optionalText(row, "gst_number", 255, validation);
            if (validation.size() == before) {
                customers.add(new CustomerRow(row, source, name, email, businessName, address, city, province,
                    postalCode, country, businessNumber, gstNumber));
            }
        }

        List<VendorRow> vendors = new ArrayList<>();
        Set<String> vendorSources = new HashSet<>();
        Set<String> vendorEmails = new HashSet<>();
        for (CsvRecord row : csv.get("vendors.csv")) {
            int before = validation.size();
            String source = sourceId(row, "source_id", vendorSources, validation);
            String name = requiredText(row, "name", 255, validation);
            String email = uniqueEmail(row, "email", vendorEmails, validation);
            String businessName = requiredText(row, "business_name", 255, validation);
            String address = requiredText(row, "address", 255, validation);
            String city = requiredText(row, "city", 255, validation);
            String province = requiredText(row, "province", 255, validation);
            String postalCode = requiredText(row, "postal_code", 255, validation);
            String country = requiredText(row, "country", 255, validation);
            String businessNumber = optionalText(row, "business_number", 255, validation);
            String gstNumber = optionalText(row, "gst_number", 255, validation);
            String qstNumber = optionalText(row, "qst_number", 255, validation);
            if (validation.size() == before) {
                vendors.add(new VendorRow(row, source, name, email, businessName, address, city, province,
                    postalCode, country, businessNumber, gstNumber, qstNumber));
            }
        }

        List<InvoiceRow> invoices = new ArrayList<>();
        Set<String> invoiceSources = new HashSet<>();
        Set<String> invoiceNumbers = new HashSet<>();
        for (CsvRecord row : csv.get("invoices.csv")) {
            int before = validation.size();
            String source = sourceId(row, "source_id", invoiceSources, validation);
            String invoiceNumber = uniqueText(row, "invoice_number", 255, invoiceNumbers, "invoice number", validation);
            String customerSource = identifier(row, "customer_source_id", true, validation);
            LocalDate invoiceDate = dateValue(row, "invoice_date", validation);
            LocalDate dueDate = dateValue(row, "due_date", validation);
            InvoiceStatus status = enumValue(row, "status", InvoiceStatus.class, validation);
            BigDecimal subtotal = money(row, "subtotal", validation);
            BigDecimal gst = money(row, "gst_amount", validation);
            BigDecimal hst = money(row, "hst_amount", validation);
            BigDecimal qst = money(row, "qst_amount", validation);
            BigDecimal total = money(row, "total_amount", validation);
            BigDecimal paid = money(row, "paid_amount", validation);
            String taxRegime = optionalText(row, "tax_regime", 30, validation);
            String notes = optionalText(row, "notes", 1000, validation);
            if (validation.size() == before) {
                invoices.add(new InvoiceRow(row, source, invoiceNumber, customerSource, invoiceDate, dueDate, status,
                    subtotal, gst, hst, qst, total, paid, taxRegime, notes));
            }
        }

        List<InvoiceLineRow> invoiceLines = new ArrayList<>();
        Set<String> invoiceLineSources = new HashSet<>();
        for (CsvRecord row : csv.get("invoice_lines.csv")) {
            int before = validation.size();
            String source = sourceId(row, "source_id", invoiceLineSources, validation);
            String invoiceSource = identifier(row, "invoice_source_id", true, validation);
            Integer lineNumber = positiveInteger(row, "line_number", validation);
            String description = requiredText(row, "description", 255, validation);
            BigDecimal quantity = money(row, "quantity", validation);
            BigDecimal unitPrice = money(row, "unit_price", validation);
            BigDecimal total = money(row, "total", validation);
            if (validation.size() == before) {
                invoiceLines.add(new InvoiceLineRow(row, source, invoiceSource, lineNumber, description,
                    quantity, unitPrice, total));
            }
        }

        List<BillRow> bills = new ArrayList<>();
        Set<String> billSources = new HashSet<>();
        Set<String> billNumbers = new HashSet<>();
        for (CsvRecord row : csv.get("bills.csv")) {
            int before = validation.size();
            String source = sourceId(row, "source_id", billSources, validation);
            String billNumber = uniqueText(row, "bill_number", 255, billNumbers, "bill number", validation);
            String vendorSource = identifier(row, "vendor_source_id", true, validation);
            LocalDate billDate = dateValue(row, "bill_date", validation);
            LocalDate dueDate = dateValue(row, "due_date", validation);
            BillStatus status = enumValue(row, "status", BillStatus.class, validation);
            BigDecimal subtotal = money(row, "subtotal", validation);
            BigDecimal gst = money(row, "gst_amount", validation);
            BigDecimal hst = money(row, "hst_amount", validation);
            BigDecimal qst = money(row, "qst_amount", validation);
            BigDecimal total = money(row, "total_amount", validation);
            BigDecimal paid = money(row, "paid_amount", validation);
            String notes = optionalText(row, "notes", 1000, validation);
            if (validation.size() == before) {
                bills.add(new BillRow(row, source, billNumber, vendorSource, billDate, dueDate, status,
                    subtotal, gst, hst, qst, total, paid, notes));
            }
        }

        List<BillLineRow> billLines = new ArrayList<>();
        Set<String> billLineSources = new HashSet<>();
        for (CsvRecord row : csv.get("bill_lines.csv")) {
            int before = validation.size();
            String source = sourceId(row, "source_id", billLineSources, validation);
            String billSource = identifier(row, "bill_source_id", true, validation);
            Integer lineNumber = positiveInteger(row, "line_number", validation);
            String expenseAccountSource = identifier(row, "expense_account_source_id", false, validation);
            String description = requiredText(row, "description", 255, validation);
            BigDecimal quantity = money(row, "quantity", validation);
            BigDecimal unitPrice = money(row, "unit_price", validation);
            BigDecimal total = money(row, "total", validation);
            if (validation.size() == before) {
                billLines.add(new BillLineRow(row, source, billSource, lineNumber, expenseAccountSource,
                    description, quantity, unitPrice, total));
            }
        }

        List<JournalRow> journals = new ArrayList<>();
        Set<String> journalSources = new HashSet<>();
        Set<String> journalNumbers = new HashSet<>();
        for (CsvRecord row : csv.get("journal_entries.csv")) {
            int before = validation.size();
            String source = sourceId(row, "source_id", journalSources, validation);
            String journalNumber = uniqueText(row, "journal_number", 255, journalNumbers, "journal number", validation);
            LocalDate journalDate = dateValue(row, "journal_date", validation);
            String narrative = requiredText(row, "narrative", 255, validation);
            String reference = optionalText(row, "reference", 500, validation);
            JournalStatus status = enumValue(row, "status", JournalStatus.class, validation);
            LocalDateTime postedDate = dateTimeValue(row, "posted_date", validation);
            String postedBy = optionalText(row, "posted_by", 500, validation);
            if (validation.size() == before) {
                journals.add(new JournalRow(row, source, journalNumber, journalDate, narrative, reference, status,
                    postedDate, postedBy));
            }
        }

        List<JournalLineRow> journalLines = new ArrayList<>();
        Set<String> journalLineSources = new HashSet<>();
        for (CsvRecord row : csv.get("journal_lines.csv")) {
            int before = validation.size();
            String source = sourceId(row, "source_id", journalLineSources, validation);
            String journalSource = identifier(row, "journal_source_id", true, validation);
            Integer lineNumber = positiveInteger(row, "line_number", validation);
            String accountSource = identifier(row, "account_source_id", true, validation);
            BigDecimal debit = money(row, "debit", validation);
            BigDecimal credit = money(row, "credit", validation);
            Boolean cleared = boolValue(row, "cleared", validation);
            String description = optionalText(row, "description", 500, validation);
            if (validation.size() == before) {
                journalLines.add(new JournalLineRow(row, source, journalSource, lineNumber, accountSource, debit,
                    credit, cleared, description));
            }
        }

        return new MigrationPlan(rowCounts, accounts, customers, vendors, invoices, invoiceLines, bills, billLines,
            journals, journalLines);
    }

    private void validateRelationships(MigrationPlan plan, Validation validation) {
        Set<String> customerSources = sourceSet(plan.customers);
        Set<String> vendorSources = sourceSet(plan.vendors);
        Set<String> accountSources = sourceSet(plan.accounts);
        Set<String> invoiceSources = sourceSet(plan.invoices);
        Set<String> billSources = sourceSet(plan.bills);
        Set<String> journalSources = sourceSet(plan.journals);

        for (InvoiceRow invoice : plan.invoices) {
            if (!customerSources.contains(invoice.customerSourceId)) {
                validation.error(invoice.row.filename, invoice.row.lineNumber,
                    "customer_source_id does not identify a row in customers.csv.");
            }
            validateDocumentAmounts(invoice.row, invoice.subtotal, invoice.gstAmount, invoice.hstAmount,
                invoice.qstAmount, invoice.totalAmount, invoice.paidAmount,
                invoice.status == InvoiceStatus.PAID, invoice.status == InvoiceStatus.PARTIALLY_PAID, validation);
        }
        for (BillRow bill : plan.bills) {
            if (!vendorSources.contains(bill.vendorSourceId)) {
                validation.error(bill.row.filename, bill.row.lineNumber,
                    "vendor_source_id does not identify a row in vendors.csv.");
            }
            validateDocumentAmounts(bill.row, bill.subtotal, bill.gstAmount, bill.hstAmount,
                bill.qstAmount, bill.totalAmount, bill.paidAmount,
                bill.status == BillStatus.PAID, bill.status == BillStatus.PARTIALLY_PAID, validation);
        }

        Map<String, List<InvoiceLineRow>> invoiceLinesByInvoice = new HashMap<>();
        Set<String> invoiceLineNumbers = new HashSet<>();
        for (InvoiceLineRow line : plan.invoiceLines) {
            if (!invoiceSources.contains(line.invoiceSourceId)) {
                validation.error(line.row.filename, line.row.lineNumber,
                    "invoice_source_id does not identify a row in invoices.csv.");
            }
            validateLineTotal(line.row, line.quantity, line.unitPrice, line.total, validation);
            if (!invoiceLineNumbers.add(line.invoiceSourceId + '\u0000' + line.lineNumber)) {
                validation.error(line.row.filename, line.row.lineNumber,
                    "line_number must be unique within an invoice.");
            }
            invoiceLinesByInvoice.computeIfAbsent(line.invoiceSourceId, unused -> new ArrayList<>()).add(line);
        }
        for (InvoiceRow invoice : plan.invoices) {
            List<InvoiceLineRow> lines = invoiceLinesByInvoice.get(invoice.sourceId);
            if (lines == null || lines.isEmpty()) {
                validation.error(invoice.row.filename, invoice.row.lineNumber,
                    "Each invoice must have at least one row in invoice_lines.csv.");
            } else if (!sameMoney(invoice.subtotal, sumInvoiceLines(lines))) {
                validation.error(invoice.row.filename, invoice.row.lineNumber,
                    "subtotal does not equal the sum of imported invoice line totals.");
            }
        }

        Map<String, List<BillLineRow>> billLinesByBill = new HashMap<>();
        Set<String> billLineNumbers = new HashSet<>();
        for (BillLineRow line : plan.billLines) {
            if (!billSources.contains(line.billSourceId)) {
                validation.error(line.row.filename, line.row.lineNumber,
                    "bill_source_id does not identify a row in bills.csv.");
            }
            if (line.expenseAccountSourceId != null && !accountSources.contains(line.expenseAccountSourceId)) {
                validation.error(line.row.filename, line.row.lineNumber,
                    "expense_account_source_id does not identify a row in accounts.csv.");
            }
            validateLineTotal(line.row, line.quantity, line.unitPrice, line.total, validation);
            if (!billLineNumbers.add(line.billSourceId + '\u0000' + line.lineNumber)) {
                validation.error(line.row.filename, line.row.lineNumber,
                    "line_number must be unique within a bill.");
            }
            billLinesByBill.computeIfAbsent(line.billSourceId, unused -> new ArrayList<>()).add(line);
        }
        for (BillRow bill : plan.bills) {
            List<BillLineRow> lines = billLinesByBill.get(bill.sourceId);
            if (lines == null || lines.isEmpty()) {
                validation.error(bill.row.filename, bill.row.lineNumber,
                    "Each bill must have at least one row in bill_lines.csv.");
            } else if (!sameMoney(bill.subtotal, sumBillLines(lines))) {
                validation.error(bill.row.filename, bill.row.lineNumber,
                    "subtotal does not equal the sum of imported bill line totals.");
            }
        }

        Map<String, List<JournalLineRow>> linesByJournal = new HashMap<>();
        Set<String> journalLineNumbers = new HashSet<>();
        for (JournalLineRow line : plan.journalLines) {
            if (!journalSources.contains(line.journalSourceId)) {
                validation.error(line.row.filename, line.row.lineNumber,
                    "journal_source_id does not identify a row in journal_entries.csv.");
            }
            if (!accountSources.contains(line.accountSourceId)) {
                validation.error(line.row.filename, line.row.lineNumber,
                    "account_source_id does not identify a row in accounts.csv.");
            }
            if (line.debit.signum() == 0 && line.credit.signum() == 0
                    || line.debit.signum() > 0 && line.credit.signum() > 0) {
                validation.error(line.row.filename, line.row.lineNumber,
                    "A journal line must contain either a debit or a credit greater than zero.");
            }
            if (!journalLineNumbers.add(line.journalSourceId + '\u0000' + line.lineNumber)) {
                validation.error(line.row.filename, line.row.lineNumber,
                    "line_number must be unique within a journal entry.");
            }
            linesByJournal.computeIfAbsent(line.journalSourceId, unused -> new ArrayList<>()).add(line);
        }
        for (JournalRow journal : plan.journals) {
            List<JournalLineRow> lines = linesByJournal.get(journal.sourceId);
            if (lines == null || lines.size() < 2) {
                validation.error(journal.row.filename, journal.row.lineNumber,
                    "Each journal entry must have at least two rows in journal_lines.csv.");
                continue;
            }
            BigDecimal debit = lines.stream().map(JournalLineRow::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credit = lines.stream().map(JournalLineRow::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!sameMoney(debit, credit)) {
                validation.error(journal.row.filename, journal.row.lineNumber,
                    "Journal debits must equal journal credits.");
            }
        }
    }

    private void validateDocumentAmounts(CsvRecord row, BigDecimal subtotal, BigDecimal gst, BigDecimal hst,
                                         BigDecimal qst, BigDecimal total, BigDecimal paid,
                                         boolean paidStatus, boolean partiallyPaidStatus, Validation validation) {
        if (!sameMoney(subtotal.add(gst).add(hst).add(qst), total)) {
            validation.error(row.filename, row.lineNumber,
                "total_amount must equal subtotal plus gst_amount, hst_amount, and qst_amount.");
        }
        if (paid.compareTo(total) > 0) {
            validation.error(row.filename, row.lineNumber, "paid_amount cannot exceed total_amount.");
        }
        if (paidStatus && !sameMoney(paid, total)) {
            validation.error(row.filename, row.lineNumber,
                "A PAID document must have paid_amount equal to total_amount.");
        }
        if (partiallyPaidStatus && (paid.signum() <= 0 || paid.compareTo(total) >= 0)) {
            validation.error(row.filename, row.lineNumber,
                "A PARTIALLY_PAID document must have paid_amount greater than zero and less than total_amount.");
        }
    }

    private void validateLineTotal(CsvRecord row, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total,
                                   Validation validation) {
        BigDecimal calculated = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        if (!sameMoney(calculated, total)) {
            validation.error(row.filename, row.lineNumber,
                "total must equal quantity multiplied by unit_price, rounded to two decimal places.");
        }
    }

    private void importPlan(MigrationPlan plan, Company company) {
        Map<String, ChartOfAccount> accounts = new HashMap<>();
        for (AccountRow source : plan.accounts) {
            ChartOfAccount account = new ChartOfAccount();
            account.setCompany(company);
            account.setAccountNumber(source.accountNumber);
            account.setAccountName(source.accountName);
            account.setAccountType(source.accountType);
            account.setDescription(source.description);
            account.setActive(source.active);
            accounts.put(source.sourceId, accountRepository.save(account));
        }

        Map<String, GeneralLedger> ledgers = new HashMap<>();
        for (Map.Entry<String, ChartOfAccount> entry : accounts.entrySet()) {
            GeneralLedger ledger = new GeneralLedger();
            ledger.setCompany(company);
            ledger.setAccount(entry.getValue());
            ledger.setDebitBalance(BigDecimal.ZERO);
            ledger.setCreditBalance(BigDecimal.ZERO);
            ledgers.put(entry.getKey(), ledgerRepository.save(ledger));
        }

        Map<String, Customer> customers = new HashMap<>();
        for (CustomerRow source : plan.customers) {
            Customer customer = new Customer();
            customer.setCompany(company);
            customer.setName(source.name);
            customer.setEmail(source.email);
            customer.setBusinessName(source.businessName);
            customer.setAddress(source.address);
            customer.setCity(source.city);
            customer.setProvince(source.province);
            customer.setPostalCode(source.postalCode);
            customer.setCountry(source.country);
            customer.setBusinessNumber(source.businessNumber);
            customer.setGstNumber(source.gstNumber);
            customers.put(source.sourceId, customerRepository.save(customer));
        }

        Map<String, Vendor> vendors = new HashMap<>();
        for (VendorRow source : plan.vendors) {
            Vendor vendor = new Vendor();
            vendor.setCompany(company);
            vendor.setName(source.name);
            vendor.setEmail(source.email);
            vendor.setBusinessName(source.businessName);
            vendor.setAddress(source.address);
            vendor.setCity(source.city);
            vendor.setProvince(source.province);
            vendor.setPostalCode(source.postalCode);
            vendor.setCountry(source.country);
            vendor.setBusinessNumber(source.businessNumber);
            vendor.setGstNumber(source.gstNumber);
            vendor.setQstNumber(source.qstNumber);
            vendors.put(source.sourceId, vendorRepository.save(vendor));
        }

        Map<String, List<InvoiceLineRow>> invoiceLinesByInvoice = groupInvoiceLines(plan.invoiceLines);
        for (InvoiceRow source : plan.invoices) {
            Invoice invoice = new Invoice();
            invoice.setCompany(company);
            invoice.setInvoiceNumber(source.invoiceNumber);
            invoice.setCustomer(customers.get(source.customerSourceId));
            invoice.setInvoiceDate(source.invoiceDate);
            invoice.setDueDate(source.dueDate);
            invoice.setStatus(source.status);
            invoice.setSubtotal(source.subtotal);
            invoice.setGstAmount(source.gstAmount);
            invoice.setHstAmount(source.hstAmount);
            invoice.setQstAmount(source.qstAmount);
            invoice.setTotalAmount(source.totalAmount);
            invoice.setPaidAmount(source.paidAmount);
            invoice.setOpeningPaidAmount(source.paidAmount);
            invoice.setTaxRegime(source.taxRegime);
            invoice.setNotes(source.notes);
            for (InvoiceLineRow sourceLine : invoiceLinesByInvoice.get(source.sourceId)) {
                LineItem line = new LineItem();
                line.setInvoice(invoice);
                line.setDescription(sourceLine.description);
                line.setQuantity(sourceLine.quantity);
                line.setUnitPrice(sourceLine.unitPrice);
                invoice.getLineItems().add(line);
            }
            invoiceRepository.save(invoice);
        }

        Map<String, List<BillLineRow>> billLinesByBill = groupBillLines(plan.billLines);
        for (BillRow source : plan.bills) {
            Bill bill = new Bill();
            bill.setCompany(company);
            bill.setBillNumber(source.billNumber);
            bill.setVendor(vendors.get(source.vendorSourceId));
            bill.setBillDate(source.billDate);
            bill.setDueDate(source.dueDate);
            bill.setStatus(source.status);
            bill.setSubtotal(source.subtotal);
            bill.setGstAmount(source.gstAmount);
            bill.setHstAmount(source.hstAmount);
            bill.setQstAmount(source.qstAmount);
            bill.setTotalAmount(source.totalAmount);
            bill.setPaidAmount(source.paidAmount);
            bill.setNotes(source.notes);
            for (BillLineRow sourceLine : billLinesByBill.get(source.sourceId)) {
                BillLineItem line = new BillLineItem();
                line.setBill(bill);
                line.setExpenseAccount(sourceLine.expenseAccountSourceId == null
                    ? null : accounts.get(sourceLine.expenseAccountSourceId));
                line.setDescription(sourceLine.description);
                line.setQuantity(sourceLine.quantity);
                line.setUnitPrice(sourceLine.unitPrice);
                bill.getLineItems().add(line);
            }
            billRepository.save(bill);
        }

        Map<String, List<JournalLineRow>> linesByJournal = groupJournalLines(plan.journalLines);
        for (JournalRow source : plan.journals) {
            GeneralJournal journal = new GeneralJournal();
            journal.setCompany(company);
            journal.setJournalNumber(source.journalNumber);
            journal.setJournalDate(source.journalDate);
            journal.setNarrative(source.narrative);
            journal.setReference(source.reference);
            journal.setStatus(source.status);
            journal.setPostedDate(source.postedDate);
            journal.setPostedBy(source.postedBy);
            for (JournalLineRow sourceLine : linesByJournal.get(source.sourceId)) {
                JournalEntry line = new JournalEntry();
                line.setJournal(journal);
                line.setAccount(accounts.get(sourceLine.accountSourceId));
                line.setDebit(sourceLine.debit);
                line.setCredit(sourceLine.credit);
                line.setCleared(sourceLine.cleared);
                line.setDescription(sourceLine.description);
                line.setLineNumber(sourceLine.lineNumber);
                journal.getEntries().add(line);
            }
            journalRepository.save(journal);
            if (source.status == JournalStatus.POSTED || source.status == JournalStatus.REVERSED) {
                for (JournalLineRow sourceLine : linesByJournal.get(source.sourceId)) {
                    GeneralLedger ledger = ledgers.get(sourceLine.accountSourceId);
                    ledger.setDebitBalance(ledger.getDebitBalance().add(sourceLine.debit));
                    ledger.setCreditBalance(ledger.getCreditBalance().add(sourceLine.credit));
                }
            }
        }
        ledgerRepository.saveAll(ledgers.values());
    }

    private boolean isMigrationDomainEmpty(Company company) {
        Long companyId = company.getId();
        return accountRepository.findAllByCompanyIdOrderByAccountNumberAsc(companyId).isEmpty()
            && customerRepository.findAllByCompanyIdOrderByBusinessNameAsc(companyId).isEmpty()
            && vendorRepository.findAllByCompanyIdOrderByBusinessNameAsc(companyId).isEmpty()
            && invoiceRepository.findAllByCompanyIdOrderByInvoiceNumberDesc(companyId).isEmpty()
            && billRepository.findAllByCompanyIdOrderByBillNumberDesc(companyId).isEmpty()
            && journalRepository.findAllByCompanyId(companyId).isEmpty()
            && ledgerRepository.findAllByCompanyIdOrderByAccountAccountNumberAsc(companyId).isEmpty();
    }

    private Company requireCompany(String email) {
        return userAccountRepository.findByEmail(email)
            .filter(user -> user.isEnabled() && user.getCompany() != null)
            .map(user -> user.getCompany())
            .orElseThrow(() -> new IllegalStateException("The migration user does not have an active company."));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void pruneExpiredPlans() {
        Instant now = Instant.now();
        pendingPlans.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }

    private String sourceId(CsvRecord row, String field, Set<String> seen, Validation validation) {
        String source = identifier(row, field, true, validation);
        if (source != null && !seen.add(source)) {
            validation.error(row.filename, row.lineNumber, "source_id must be unique within its CSV file.");
        }
        return source;
    }

    private String identifier(CsvRecord row, String field, boolean required, Validation validation) {
        String value = required ? requiredText(row, field, 100, validation) : optionalText(row, field, 100, validation);
        if (value != null && !value.matches("[A-Za-z0-9._:-]+")) {
            validation.error(row.filename, row.lineNumber, field + " must contain only letters, digits, dot, underscore, colon, or hyphen.");
            return null;
        }
        return value;
    }

    private String uniqueText(CsvRecord row, String field, int maximumLength, Set<String> values, String label,
                              Validation validation) {
        String value = requiredText(row, field, maximumLength, validation);
        if (value != null && !values.add(value.toLowerCase(Locale.ROOT))) {
            validation.error(row.filename, row.lineNumber, "Duplicate " + label + " in this migration package.");
        }
        return value;
    }

    private String uniqueEmail(CsvRecord row, String field, Set<String> values, Validation validation) {
        String email = requiredText(row, field, 255, validation);
        if (email != null && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            validation.error(row.filename, row.lineNumber, "Email address is invalid.");
        }
        if (email != null && !values.add(email.toLowerCase(Locale.ROOT))) {
            validation.error(row.filename, row.lineNumber, "Duplicate email in this migration package.");
        }
        return email;
    }

    private String requiredText(CsvRecord row, String field, int maximumLength, Validation validation) {
        String value = cleanText(row, field, maximumLength, validation);
        if (value == null || value.isEmpty()) {
            validation.error(row.filename, row.lineNumber, field + " is required.");
            return null;
        }
        return value;
    }

    private String optionalText(CsvRecord row, String field, int maximumLength, Validation validation) {
        return cleanText(row, field, maximumLength, validation);
    }

    private String cleanText(CsvRecord row, String field, int maximumLength, Validation validation) {
        String raw = row.values.get(field);
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maximumLength) {
            validation.error(row.filename, row.lineNumber, field + " exceeds the maximum permitted length.");
            return null;
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.chars().anyMatch(ch -> ch < 0x20)) {
            validation.error(row.filename, row.lineNumber, field + " contains an unsupported control character.");
            return null;
        }
        if ("=+-@".indexOf(value.charAt(0)) >= 0) {
            validation.error(row.filename, row.lineNumber, field + " must not begin with a spreadsheet formula character.");
            return null;
        }
        return value;
    }

    private Boolean boolValue(CsvRecord row, String field, Validation validation) {
        String value = requiredText(row, field, 5, validation);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        if (value != null) {
            validation.error(row.filename, row.lineNumber, field + " must be true or false.");
        }
        return null;
    }

    private Integer positiveInteger(CsvRecord row, String field, Validation validation) {
        String value = requiredText(row, field, 10, validation);
        if (value == null || !value.matches("[1-9][0-9]*")) {
            if (value != null) {
                validation.error(row.filename, row.lineNumber, field + " must be a positive integer.");
            }
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            validation.error(row.filename, row.lineNumber, field + " is outside the supported range.");
            return null;
        }
    }

    private BigDecimal money(CsvRecord row, String field, Validation validation) {
        String value = requiredText(row, field, 20, validation);
        if (value == null || !value.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            if (value != null) {
                validation.error(row.filename, row.lineNumber, field + " must be a non-negative decimal with at most two places.");
            }
            return null;
        }
        BigDecimal number = new BigDecimal(value);
        if (number.precision() - number.scale() > 17) {
            validation.error(row.filename, row.lineNumber, field + " exceeds the supported monetary range.");
            return null;
        }
        return number.setScale(2);
    }

    private LocalDate dateValue(CsvRecord row, String field, Validation validation) {
        String value = requiredText(row, field, 10, validation);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            validation.error(row.filename, row.lineNumber, field + " must be an ISO-8601 date (YYYY-MM-DD).");
            return null;
        }
    }

    private LocalDateTime dateTimeValue(CsvRecord row, String field, Validation validation) {
        String value = optionalText(row, field, 35, validation);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            validation.error(row.filename, row.lineNumber,
                field + " must be an ISO-8601 local date-time (YYYY-MM-DDTHH:MM:SS).");
            return null;
        }
    }

    private <E extends Enum<E>> E enumValue(CsvRecord row, String field, Class<E> type, Validation validation) {
        String value = requiredText(row, field, 32, validation);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            validation.error(row.filename, row.lineNumber, field + " is not a supported value.");
            return null;
        }
    }

    private static boolean sameMoney(BigDecimal first, BigDecimal second) {
        return first != null && second != null && first.compareTo(second) == 0;
    }

    private static BigDecimal sumInvoiceLines(List<InvoiceLineRow> lines) {
        return lines.stream().map(InvoiceLineRow::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumBillLines(List<BillLineRow> lines) {
        return lines.stream().map(BillLineRow::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static <T extends SourceRow> Set<String> sourceSet(List<T> rows) {
        Set<String> result = new HashSet<>();
        for (T row : rows) {
            result.add(row.sourceId());
        }
        return result;
    }

    private static Map<String, List<InvoiceLineRow>> groupInvoiceLines(List<InvoiceLineRow> rows) {
        Map<String, List<InvoiceLineRow>> grouped = new HashMap<>();
        for (InvoiceLineRow row : rows) {
            grouped.computeIfAbsent(row.invoiceSourceId, ignored -> new ArrayList<>()).add(row);
        }
        grouped.values().forEach(lines -> lines.sort(Comparator.comparingInt(InvoiceLineRow::lineNumber)));
        return grouped;
    }

    private static Map<String, List<BillLineRow>> groupBillLines(List<BillLineRow> rows) {
        Map<String, List<BillLineRow>> grouped = new HashMap<>();
        for (BillLineRow row : rows) {
            grouped.computeIfAbsent(row.billSourceId, ignored -> new ArrayList<>()).add(row);
        }
        grouped.values().forEach(lines -> lines.sort(Comparator.comparingInt(BillLineRow::lineNumber)));
        return grouped;
    }

    private static Map<String, List<JournalLineRow>> groupJournalLines(List<JournalLineRow> rows) {
        Map<String, List<JournalLineRow>> grouped = new HashMap<>();
        for (JournalLineRow row : rows) {
            grouped.computeIfAbsent(row.journalSourceId, ignored -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private record CsvRow(int lineNumber, List<String> values) {
    }

    private record CsvRecord(String filename, int lineNumber, Map<String, String> values) {
    }

    private interface SourceRow {
        String sourceId();
    }

    private record AccountRow(CsvRecord row, String sourceId, String accountNumber, String accountName,
                              AccountType accountType, String description, Boolean active) implements SourceRow {
    }

    private record CustomerRow(CsvRecord row, String sourceId, String name, String email, String businessName,
                               String address, String city, String province, String postalCode, String country,
                               String businessNumber, String gstNumber) implements SourceRow {
    }

    private record VendorRow(CsvRecord row, String sourceId, String name, String email, String businessName,
                             String address, String city, String province, String postalCode, String country,
                             String businessNumber, String gstNumber, String qstNumber) implements SourceRow {
    }

    private record InvoiceRow(CsvRecord row, String sourceId, String invoiceNumber, String customerSourceId,
                              LocalDate invoiceDate, LocalDate dueDate, InvoiceStatus status, BigDecimal subtotal,
                              BigDecimal gstAmount, BigDecimal hstAmount, BigDecimal qstAmount, BigDecimal totalAmount,
                              BigDecimal paidAmount, String taxRegime, String notes) implements SourceRow {
    }

    private record InvoiceLineRow(CsvRecord row, String sourceId, String invoiceSourceId, Integer lineNumber,
                                  String description, BigDecimal quantity, BigDecimal unitPrice,
                                  BigDecimal total) implements SourceRow {
    }

    private record BillRow(CsvRecord row, String sourceId, String billNumber, String vendorSourceId,
                           LocalDate billDate, LocalDate dueDate, BillStatus status, BigDecimal subtotal,
                           BigDecimal gstAmount, BigDecimal hstAmount, BigDecimal qstAmount, BigDecimal totalAmount,
                           BigDecimal paidAmount, String notes) implements SourceRow {
    }

    private record BillLineRow(CsvRecord row, String sourceId, String billSourceId, Integer lineNumber,
                               String expenseAccountSourceId, String description, BigDecimal quantity,
                               BigDecimal unitPrice, BigDecimal total) implements SourceRow {
    }

    private record JournalRow(CsvRecord row, String sourceId, String journalNumber, LocalDate journalDate,
                              String narrative, String reference, JournalStatus status, LocalDateTime postedDate,
                              String postedBy) implements SourceRow {
    }

    private record JournalLineRow(CsvRecord row, String sourceId, String journalSourceId, Integer lineNumber,
                                  String accountSourceId, BigDecimal debit, BigDecimal credit, Boolean cleared,
                                  String description) implements SourceRow {
    }

    private record MigrationPlan(Map<String, Integer> rowCounts, List<AccountRow> accounts,
                                 List<CustomerRow> customers, List<VendorRow> vendors, List<InvoiceRow> invoices,
                                 List<InvoiceLineRow> invoiceLines, List<BillRow> bills, List<BillLineRow> billLines,
                                 List<JournalRow> journals, List<JournalLineRow> journalLines) {
        static MigrationPlan empty() {
            return withCounts(Map.of());
        }

        static MigrationPlan withCounts(Map<String, Integer> rowCounts) {
            return new MigrationPlan(Map.copyOf(rowCounts), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
        }
    }

    private record PendingPlan(String requestedBy, Instant expiresAt, MigrationPlan plan) {
    }

    private static final class Validation {
        private final List<MigrationValidationError> errors = new ArrayList<>();
        private int findings;

        void error(String filename, int rowNumber, String message) {
            findings++;
            if (errors.size() < MAX_ERRORS) {
                errors.add(new MigrationValidationError(filename, rowNumber, message));
            }
        }

        boolean hasErrors() {
            return findings > 0;
        }

        int size() {
            return findings;
        }
    }
}
