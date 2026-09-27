package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.BankReconciliationSessionRepository;
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
    private final ChartOfAccountRepository accountRepository;
    private final GeneralLedgerRepository ledgerRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final CurrentCompanyContext companyContext;

    public BankReconciliationService(BankTransactionRepository bankTransactionRepository,
                                     BankReconciliationSessionRepository sessionRepository,
                                     ChartOfAccountRepository accountRepository,
                                     GeneralLedgerRepository ledgerRepository,
                                     JournalEntryRepository journalEntryRepository,
                                     CurrentCompanyContext companyContext) {
        this.bankTransactionRepository = bankTransactionRepository;
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.journalEntryRepository = journalEntryRepository;
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
                                                Map<Long, Long> matches) {
        if (statementDate == null) {
            throw new IllegalArgumentException("Statement date is required");
        }
        BigDecimal endingBalance = monetary(submittedEndingBalance, "Statement ending balance");
        if (matches == null || matches.isEmpty() || matches.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Every imported bank transaction must be matched");
        }

        Long companyId = companyContext.requireCompanyId();
        ChartOfAccount account = findLockedBankAccount(accountId, companyId);
        List<BankTransaction> pendingTransactions = bankTransactionRepository
                .findByCompanyIdAndBankAccountIdAndReconciledFalseOrderByTransactionDateAscIdAsc(companyId, accountId);
        Set<Long> pendingIds = pendingTransactions.stream().map(BankTransaction::getId).collect(Collectors.toSet());

        if (!pendingIds.equals(matches.keySet())) {
            throw new IllegalArgumentException("All unreconciled bank transactions must be matched exactly once");
        }
        Set<Long> entryIds = new HashSet<>(matches.values());
        if (entryIds.size() != matches.size()) {
            throw new IllegalArgumentException("A journal entry can only be matched to one bank transaction");
        }

        List<JournalEntry> entries = journalEntryRepository.findByIdInAndJournalCompanyId(
                new ArrayList<>(entryIds), companyId);
        if (entries.size() != entryIds.size()) {
            throw new IllegalArgumentException("One or more selected journal entries do not belong to your company");
        }
        Map<Long, JournalEntry> entriesById = entries.stream()
                .collect(Collectors.toMap(JournalEntry::getId, entry -> entry));

        for (BankTransaction transaction : pendingTransactions) {
            if (transaction.isReconciled()) {
                throw new IllegalArgumentException("A selected bank transaction has already been reconciled");
            }
            JournalEntry entry = entriesById.get(matches.get(transaction.getId()));
            validateMatch(accountId, transaction, entry);
        }

        BigDecimal openingBalance = openingBalance(companyId, account);
        BigDecimal transactionTotal = pendingTransactions.stream()
                .map(BankTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);
        if (openingBalance.add(transactionTotal).compareTo(endingBalance) != 0) {
            throw new IllegalArgumentException("Opening balance plus imported bank transactions does not equal the statement ending balance");
        }

        BankReconciliationSession session = new BankReconciliationSession();
        session.setCompany(companyContext.requireCompany());
        session.setBankAccount(account);
        session.setStatementDate(statementDate);
        session.setOpeningBalance(openingBalance);
        session.setTransactionTotal(transactionTotal);
        session.setEndingBalance(endingBalance);
        session.setCompletedAt(LocalDateTime.now());
        session = sessionRepository.save(session);

        for (BankTransaction transaction : pendingTransactions) {
            transaction.setReconciled(true);
            transaction.setReconciliationSession(session);
        }
        for (JournalEntry entry : entries) {
            entry.setCleared(true);
        }
        bankTransactionRepository.saveAll(pendingTransactions);
        journalEntryRepository.saveAll(entries);
        return session;
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

    private void validateMatch(Long accountId, BankTransaction transaction, JournalEntry entry) {
        if (entry == null
                || entry.isCleared()
                || entry.getJournal() == null
                || entry.getJournal().getStatus() != JournalStatus.POSTED
                || entry.getAccount() == null
                || !accountId.equals(entry.getAccount().getId())) {
            throw new IllegalArgumentException("Selected journal entry is not an eligible uncleared posted entry");
        }
        BigDecimal journalAmount = entry.getDebit().subtract(entry.getCredit()).setScale(2, RoundingMode.UNNECESSARY);
        if (transaction.getAmount().compareTo(journalAmount) != 0) {
            throw new IllegalArgumentException("Bank transaction and journal entry amounts must have the same signed value");
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
