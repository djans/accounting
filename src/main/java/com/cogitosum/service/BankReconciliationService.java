package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.BankReconciliationSessionRepository;
import com.cogitosum.repository.BankReconciliationLineRepository;
import com.cogitosum.repository.BankTransactionRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.repository.JournalEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BankReconciliationService {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M-d-uuuu").withResolverStyle(ResolverStyle.STRICT));

    private final BankTransactionRepository bankTransactionRepository;
    private final BankReconciliationSessionRepository sessionRepository;
    private final BankReconciliationLineRepository reconciliationLineRepository;
    private final ChartOfAccountRepository accountRepository;
    private final GeneralLedgerRepository ledgerRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final ChequeService chequeService;
    private final CurrentCompanyContext companyContext;

    public BankReconciliationService(BankTransactionRepository bankTransactionRepository,
                                     BankReconciliationSessionRepository sessionRepository,
                                     BankReconciliationLineRepository reconciliationLineRepository,
                                     ChartOfAccountRepository accountRepository,
                                     GeneralLedgerRepository ledgerRepository,
                                     JournalEntryRepository journalEntryRepository,
                                     ChequeService chequeService,
                                     CurrentCompanyContext companyContext) {
        this.bankTransactionRepository = bankTransactionRepository;
        this.sessionRepository = sessionRepository;
        this.reconciliationLineRepository = reconciliationLineRepository;
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.chequeService = chequeService;
        this.companyContext = companyContext;
    }

    @Transactional(readOnly = true)
    public List<BankAccountSummary> getBankAccounts() {
        Long companyId = companyContext.requireCompanyId();
        return accountRepository.findByCompanyIdAndCategory(companyId, AccountCategory.BANK).stream()
                .filter(account -> Boolean.TRUE.equals(account.getActive()))
                .map(account -> new BankAccountSummary(account,
                        ledgerRepository.findByCompanyIdAndAccountId(companyId, account.getId())
                                .map(GeneralLedger::getBalance)
                                .orElse(BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChartOfAccount getBankAccount(Long accountId) {
        return findBankAccount(accountId);
    }

    @Transactional(readOnly = true)
    public List<BankTransaction> getUnreconciledTransactions(Long accountId) {
        Long companyId = companyContext.requireCompanyId();
        findBankAccount(accountId);
        return bankTransactionRepository
                .findByCompanyIdAndBankAccountIdAndReconciledFalseOrderByTransactionDateAscIdAsc(companyId, accountId);
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> getEligibleJournalEntries(Long accountId) {
        Long companyId = companyContext.requireCompanyId();
        findBankAccount(accountId);
        return journalEntryRepository.findByJournalCompanyIdAndAccountIdAndClearedAndJournalStatus(
                companyId, accountId, false, JournalStatus.POSTED);
    }

    @Transactional(readOnly = true)
    public List<BankReconciliationLine> recoverLegacyReportLines(BankReconciliationSession session) {
        if (session.isReportLinesCaptured()) {
            return List.of();
        }

        Long companyId = companyContext.requireCompanyId();
        ChartOfAccount account = session.getBankAccount();
        LocalDate previousStatementDate = sessionRepository
                .findByCompanyIdAndBankAccountIdOrderByStatementDateDescIdDesc(companyId, account.getId())
                .stream()
                .filter(previous -> isBefore(previous, session))
                .map(BankReconciliationSession::getStatementDate)
                .findFirst()
                .orElse(account.getOpeningBalanceDate());
        LocalDate lowerBound = account.getOpeningBalanceDate();
        if (previousStatementDate != null
                && (lowerBound == null || previousStatementDate.isAfter(lowerBound))) {
            lowerBound = previousStatementDate;
        }
        LocalDate statementPeriodStart = lowerBound;

        List<JournalEntry> candidates = journalEntryRepository
                .findByJournalCompanyIdAndAccountIdAndClearedAndJournalStatus(
                        companyId, account.getId(), true, JournalStatus.POSTED)
                .stream()
                .filter(entry -> {
                    GeneralJournal journal = entry.getJournal();
                    LocalDate date = journal.getJournalDate();
                    return date != null
                            && (statementPeriodStart == null || date.isAfter(statementPeriodStart))
                            && !date.isAfter(session.getStatementDate())
                            && (session.getCompletedAt() == null || journal.getPostedDate() == null
                            || !journal.getPostedDate().isAfter(session.getCompletedAt()));
                })
                .sorted(Comparator
                        .comparing((JournalEntry entry) -> entry.getJournal().getJournalDate())
                        .thenComparing(JournalEntry::getLineNumber)
                        .thenComparing(JournalEntry::getId))
                .toList();

        List<Long> candidateIds = candidates.stream().map(JournalEntry::getId).toList();
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        Set<Long> alreadyCapturedIds = reconciliationLineRepository.findByJournalEntryIdIn(candidateIds)
                .stream()
                .map(BankReconciliationLine::getJournalEntryId)
                .collect(Collectors.toSet());
        return candidates.stream()
                .filter(entry -> !alreadyCapturedIds.contains(entry.getId()))
                .map(entry -> reportLine(session, entry))
                .toList();
    }

    private boolean isBefore(BankReconciliationSession candidate, BankReconciliationSession session) {
        int dateComparison = candidate.getStatementDate().compareTo(session.getStatementDate());
        return dateComparison < 0 || (dateComparison == 0
                && candidate.getId() != null && session.getId() != null
                && candidate.getId() < session.getId());
    }

    @Transactional(readOnly = true)
    public BigDecimal getOpeningBalance(Long accountId) {
        Long companyId = companyContext.requireCompanyId();
        ChartOfAccount account = findBankAccount(accountId);
        return openingBalance(companyId, account);
    }

    /**
     * Parses and validates the entire file before any transaction is persisted.
     */
    @Transactional
    public ImportResult importCsv(Long accountId, byte[] sourceBytes) {
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new IllegalArgumentException("The CSV file is empty");
        }

        List<ImportedRow> rows = parseCsv(sourceBytes);
        Long companyId = companyContext.requireCompanyId();
        ChartOfAccount account = findBankAccount(accountId);
        String sourceHash = sha256(sourceBytes);

        if (bankTransactionRepository.existsByCompanyIdAndBankAccountIdAndSourceHash(
                companyId, accountId, sourceHash)) {
            throw new IllegalArgumentException("This CSV file was already imported for this bank account");
        }

        Set<String> rowHashes = rows.stream().map(ImportedRow::rowHash).collect(Collectors.toSet());
        if (rowHashes.size() != rows.size()) {
            throw new IllegalArgumentException("The CSV contains duplicate transaction rows");
        }

        if (!bankTransactionRepository
                .findByCompanyIdAndBankAccountIdAndSourceRowHashIn(companyId, accountId, rowHashes)
                .isEmpty()) {
            throw new IllegalArgumentException("One or more CSV rows were already imported for this bank account");
        }

        List<BankTransaction> transactions = new ArrayList<>(rows.size());
        for (ImportedRow row : rows) {
            BankTransaction transaction = new BankTransaction();
            transaction.setCompany(companyContext.requireCompany());
            transaction.setBankAccount(account);
            transaction.setTransactionDate(row.transactionDate());
            transaction.setDescription(row.description());
            transaction.setAmount(row.amount());
            transaction.setReference(row.reference());
            transaction.setSourceHash(sourceHash);
            transaction.setSourceRowHash(row.rowHash());
            transaction.setReconciled(false);
            transactions.add(transaction);
        }
        bankTransactionRepository.saveAll(transactions);
        return new ImportResult(transactions.size(), sourceHash);
    }

    /**
     * Completes a statement as one database transaction. The opening balance,
     * transaction amounts, account ownership, and entry eligibility are all
     * loaded on the server and never accepted from the browser.
     */
    @Transactional
    public BankReconciliationSession reconcile(Long accountId,
                                                LocalDate statementDate,
                                                BigDecimal submittedEndingBalance,
                                                List<Long> selectedEntryIds) {
        if (statementDate == null) {
            throw new IllegalArgumentException("Statement date is required");
        }
        BigDecimal endingBalance = monetary(submittedEndingBalance, "Statement ending balance");
        List<Long> submittedIds = selectedEntryIds == null ? List.of() : selectedEntryIds;
        Set<Long> uniqueEntryIds = new LinkedHashSet<>(submittedIds);
        if (uniqueEntryIds.contains(null) || uniqueEntryIds.size() != submittedIds.size()) {
            throw new IllegalArgumentException("Selected journal entries must be unique and valid");
        }

        Long companyId = companyContext.requireCompanyId();
        ChartOfAccount account = findLockedBankAccount(accountId, companyId);
        List<JournalEntry> entries = uniqueEntryIds.isEmpty()
                ? List.of()
                : journalEntryRepository.findByIdInAndJournalCompanyId(
                        new ArrayList<>(uniqueEntryIds), companyId);
        if (entries.size() != uniqueEntryIds.size()) {
            throw new IllegalArgumentException("One or more selected journal entries do not belong to your company");
        }
        for (JournalEntry entry : entries) {
            validateEligibleEntry(accountId, statementDate, entry);
        }

        BigDecimal openingBalance = openingBalance(companyId, account);
        BigDecimal transactionTotal = entries.stream()
                .map(entry -> entry.getDebit().subtract(entry.getCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);
        if (openingBalance.add(transactionTotal).compareTo(endingBalance) != 0) {
            throw new IllegalArgumentException(
                    "Opening balance plus selected journal entries does not equal the statement ending balance");
        }

        BankReconciliationSession session = new BankReconciliationSession();
        session.setCompany(companyContext.requireCompany());
        session.setBankAccount(account);
        session.setStatementDate(statementDate);
        session.setOpeningBalance(openingBalance);
        session.setTransactionTotal(transactionTotal);
        session.setEndingBalance(endingBalance);
        session.setRegisterBalance(registerBalanceAt(companyId, account, statementDate));
        session.setReportLinesCaptured(true);
        session.setCompletedAt(LocalDateTime.now());
        BankReconciliationSession savedSession = sessionRepository.save(session);

        List<BankReconciliationLine> reportLines = entries.stream()
                .map(entry -> reportLine(savedSession, entry))
                .toList();
        if (!reportLines.isEmpty()) {
            reconciliationLineRepository.saveAll(reportLines);
        }

        for (JournalEntry entry : entries) {
            entry.setCleared(true);
            chequeService.markClearedByJournal(entry.getJournal());
        }
        if (!entries.isEmpty()) {
            journalEntryRepository.saveAll(entries);
        }
        return savedSession;
    }

    private BigDecimal registerBalanceAt(Long companyId, ChartOfAccount account, LocalDate statementDate) {
        LocalDate openingBalanceDate = account.getOpeningBalanceDate();
        if (openingBalanceDate != null && openingBalanceDate.isAfter(statementDate)) {
            return null;
        }
        BigDecimal openingBalance = account.getOpeningBalance() == null
                ? BigDecimal.ZERO
                : account.getOpeningBalance();
        BigDecimal activity = journalEntryRepository.findPostedEntriesForAccountThroughDate(
                        companyId, account.getId(), JournalStatus.POSTED, statementDate).stream()
                .filter(entry -> openingBalanceDate == null
                        || entry.getJournal().getJournalDate().isAfter(openingBalanceDate))
                .map(entry -> entry.getDebit().subtract(entry.getCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return openingBalance.add(activity).setScale(2, RoundingMode.UNNECESSARY);
    }

    private BankReconciliationLine reportLine(BankReconciliationSession session, JournalEntry entry) {
        GeneralJournal journal = entry.getJournal();
        BankReconciliationLine line = new BankReconciliationLine();
        line.setSession(session);
        line.setJournalEntryId(entry.getId());
        line.setTransactionType(transactionType(journal.getReference()));
        line.setTransactionDate(journal.getJournalDate());
        line.setDocumentNumber(documentNumber(
                line.getTransactionType(), journal.getReference(), journal.getNarrative()));
        line.setName(transactionName(line.getTransactionType(), journal.getNarrative(), entry));
        line.setDescription(entry.getDescription() == null || entry.getDescription().isBlank()
                ? journal.getNarrative()
                : entry.getDescription());
        line.setAmount(entry.getDebit().subtract(entry.getCredit())
                .setScale(2, RoundingMode.UNNECESSARY));
        return line;
    }

    private BankReconciliationLineType transactionType(String reference) {
        if (reference == null) {
            return BankReconciliationLineType.GENERAL_JOURNAL;
        }
        String normalizedReference = reference.toUpperCase(Locale.ROOT);
        if (normalizedReference.startsWith("CHEQUE-")) {
            return BankReconciliationLineType.CHEQUE;
        }
        if (normalizedReference.startsWith("TRANSFER-")) {
            return BankReconciliationLineType.TRANSFER;
        }
        if (normalizedReference.startsWith("PAYMENT-")
                || normalizedReference.startsWith("BILLPAYMENT-")
                || normalizedReference.startsWith("TAX-PAYMENT-")) {
            return BankReconciliationLineType.PAYMENT;
        }
        return BankReconciliationLineType.GENERAL_JOURNAL;
    }

    private String documentNumber(BankReconciliationLineType type, String reference, String narrative) {
        if (type == BankReconciliationLineType.TRANSFER) {
            return reference;
        }
        if (narrative == null) {
            return null;
        }
        String prefix = switch (type) {
            case CHEQUE -> "Cheque ";
            case PAYMENT -> narrative.startsWith("Bill payment ")
                    ? "Bill payment "
                    : "Payment ";
            default -> null;
        };
        if (prefix == null || !narrative.startsWith(prefix)) {
            return null;
        }
        int suffix = narrative.indexOf(type == BankReconciliationLineType.CHEQUE ? " - " : " for ", prefix.length());
        return suffix < 0 ? narrative.substring(prefix.length()) : narrative.substring(prefix.length(), suffix);
    }

    private String transactionName(BankReconciliationLineType type, String narrative, JournalEntry entry) {
        if (entry.getCustomer() != null) {
            return entry.getCustomer().getBusinessName();
        }
        if (entry.getVendor() != null) {
            return entry.getVendor().getBusinessName();
        }
        if (entry.getTaxAgency() != null) {
            return entry.getTaxAgency().getName();
        }
        if (type == BankReconciliationLineType.CHEQUE && narrative != null) {
            int separator = narrative.indexOf(" - ");
            if (separator >= 0 && separator + 3 < narrative.length()) {
                return narrative.substring(separator + 3);
            }
        }
        if (type == BankReconciliationLineType.TRANSFER && entry.getJournal() != null) {
            return entry.getJournal().getEntries().stream()
                    .filter(other -> other.getAccount() != null
                            && !other.getAccount().getId().equals(entry.getAccount().getId()))
                    .map(other -> other.getAccount().getAccountName())
                    .findFirst()
                    .orElse(narrative);
        }
        return narrative;
    }

    private ChartOfAccount findBankAccount(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("Bank account is required");
        }
        Long companyId = companyContext.requireCompanyId();
        return accountRepository.findByIdAndCompanyId(accountId, companyId)
                .filter(this::isBankAccount)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));
    }

    private ChartOfAccount findLockedBankAccount(Long accountId, Long companyId) {
        if (accountId == null) {
            throw new IllegalArgumentException("Bank account is required");
        }
        return accountRepository.findLockedByIdAndCompanyId(accountId, companyId)
                .filter(this::isBankAccount)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));
    }

    private boolean isBankAccount(ChartOfAccount account) {
        return account.getCategory() == AccountCategory.BANK;
    }

    private BigDecimal openingBalance(Long companyId, ChartOfAccount account) {
        return sessionRepository.findFirstByCompanyIdAndBankAccountIdOrderByStatementDateDescIdDesc(companyId, account.getId())
                .map(BankReconciliationSession::getEndingBalance)
                .orElseGet(() -> account.getOpeningBalance() == null
                        ? BigDecimal.ZERO
                        : account.getOpeningBalance())
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    private void validateEligibleEntry(Long accountId, LocalDate statementDate, JournalEntry entry) {
        if (entry == null
                || entry.isCleared()
                || entry.getJournal() == null
                || entry.getJournal().getStatus() != JournalStatus.POSTED
                || entry.getJournal().getJournalDate() == null
                || entry.getJournal().getJournalDate().isAfter(statementDate)
                || entry.getAccount() == null
                || !accountId.equals(entry.getAccount().getId())) {
            throw new IllegalArgumentException("Selected journal entry is not an eligible uncleared posted entry");
        }
    }

    private List<ImportedRow> parseCsv(byte[] sourceBytes) {
        String csv = decodeUtf8(sourceBytes);
        if (csv.startsWith("\uFEFF")) {
            csv = csv.substring(1);
        }
        List<List<String>> records = parseRecords(csv);
        if (records.size() < 2) {
            throw new IllegalArgumentException("The CSV must contain a header and at least one transaction row");
        }

        Map<String, Integer> headers = headers(records.get(0));
        List<ImportedRow> rows = new ArrayList<>();
        for (int recordIndex = 1; recordIndex < records.size(); recordIndex++) {
            List<String> record = records.get(recordIndex);
            int csvRow = recordIndex + 1;
            if (record.size() != headers.size()) {
                throw new IllegalArgumentException("CSV row " + csvRow + " has a different number of columns than the header");
            }
            LocalDate date = parseDate(value(record, headers, "date"), csvRow);
            String description = requiredText(value(record, headers, "description"), "Description", csvRow, 1000);
            BigDecimal amount = parseAmount(value(record, headers, "amount"), csvRow);
            String reference = headers.containsKey("reference")
                    ? optionalText(value(record, headers, "reference"), "Reference", csvRow, 500)
                    : null;
            rows.add(new ImportedRow(date, description, amount, reference,
                    sha256(canonicalRow(date, description, amount, reference).getBytes(StandardCharsets.UTF_8))));
        }
        return rows;
    }

    private Map<String, Integer> headers(List<String> record) {
        if (record.isEmpty()) {
            throw new IllegalArgumentException("The CSV header is missing");
        }
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (int index = 0; index < record.size(); index++) {
            String header = record.get(index).trim().toLowerCase(Locale.ROOT);
            if (!Set.of("date", "description", "amount", "reference").contains(header)) {
                throw new IllegalArgumentException("Unsupported CSV header: " + record.get(index));
            }
            if (headers.put(header, index) != null) {
                throw new IllegalArgumentException("Duplicate CSV header: " + record.get(index));
            }
        }
        for (String required : List.of("date", "description", "amount")) {
            if (!headers.containsKey(required)) {
                throw new IllegalArgumentException("Missing required CSV header: " + required);
            }
        }
        return headers;
    }

    private String value(List<String> record, Map<String, Integer> headers, String header) {
        return record.get(headers.get(header));
    }

    private LocalDate parseDate(String value, int row) {
        String dateText = requiredText(value, "Date", row, 50);
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateText, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported statement date format.
            }
        }
        throw new IllegalArgumentException("CSV row " + row + " has an invalid Date");
    }

    private BigDecimal parseAmount(String value, int row) {
        String amountText = requiredText(value, "Amount", row, 100);
        boolean parenthesized = amountText.startsWith("(") || amountText.endsWith(")");
        if (parenthesized) {
            if (!amountText.startsWith("(") || !amountText.endsWith(")")) {
                throw new IllegalArgumentException("CSV row " + row + " has an invalid Amount");
            }
            amountText = amountText.substring(1, amountText.length() - 1).trim();
        }
        String normalized = amountText.replace("$", "").replace(" ", "");
        String numericPattern = parenthesized
                ? "(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?"
                : "[+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?";
        if (!normalized.matches(numericPattern)) {
            throw new IllegalArgumentException("CSV row " + row + " has an invalid Amount");
        }
        try {
            BigDecimal amount = new BigDecimal(normalized.replace(",", ""));
            if (parenthesized) {
                amount = amount.negate();
            }
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() == 0) {
                throw new IllegalArgumentException("CSV row " + row + " Amount must not be zero");
            }
            return amount;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("CSV row " + row + " has an invalid Amount");
        }
    }

    private String requiredText(String value, String label, int row, int maxLength) {
        String normalized = optionalText(value, label, row, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException("CSV row " + row + " " + label + " is required");
        }
        return normalized;
    }

    private String optionalText(String value, String label, int row, int maxLength) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("CSV row " + row + " " + label + " is too long");
        }
        return normalized;
    }

    private List<List<String>> parseRecords(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean afterQuote = false;
        String normalized = csv.replace("\r\n", "\n").replace('\r', '\n');

        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (inQuotes) {
                if (character == '"') {
                    if (index + 1 < normalized.length() && normalized.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        inQuotes = false;
                        afterQuote = true;
                    }
                } else {
                    field.append(character);
                }
                continue;
            }
            if (afterQuote) {
                if (character == ',') {
                    record.add(field.toString());
                    field.setLength(0);
                    afterQuote = false;
                } else if (character == '\n') {
                    record.add(field.toString());
                    records.add(record);
                    record = new ArrayList<>();
                    field.setLength(0);
                    afterQuote = false;
                } else {
                    throw new IllegalArgumentException("Invalid CSV quoting");
                }
                continue;
            }
            if (character == '"') {
                if (field.length() > 0) {
                    throw new IllegalArgumentException("Invalid CSV quoting");
                }
                inQuotes = true;
            } else if (character == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (character == '\n') {
                record.add(field.toString());
                records.add(record);
                record = new ArrayList<>();
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Unterminated quoted CSV field");
        }
        if (!record.isEmpty() || !field.isEmpty() || afterQuote) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }

    private String decodeUtf8(byte[] sourceBytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(sourceBytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("The CSV must be UTF-8 encoded");
        }
    }

    private BigDecimal monetary(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(label + " must have no more than two decimal places");
        }
    }

    private String canonicalRow(LocalDate date, String description, BigDecimal amount, String reference) {
        return date + "|" + description.length() + ":" + description + "|" + amount.toPlainString()
                + "|" + (reference == null ? -1 : reference.length()) + ":" + (reference == null ? "" : reference);
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public static final class BankAccountSummary {
        private final ChartOfAccount account;
        private final BigDecimal balance;

        public BankAccountSummary(ChartOfAccount account, BigDecimal balance) {
            this.account = account;
            this.balance = balance;
        }

        public ChartOfAccount getAccount() {
            return account;
        }

        public BigDecimal getBalance() {
            return balance;
        }
    }

    public record ImportResult(int importedCount, String sourceHash) {
    }

    private record ImportedRow(LocalDate transactionDate, String description, BigDecimal amount,
                               String reference, String rowHash) {
    }
}
