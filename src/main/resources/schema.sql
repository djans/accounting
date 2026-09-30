-- =========================================================================
-- Accounting schema — MySQL DDL
-- Idempotent: safe to re-run on every startup
-- =========================================================================

-- 1. companies ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS companies (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(255) NOT NULL,
    legal_name              VARCHAR(255) NOT NULL,
    email                   VARCHAR(255) NOT NULL,
    phone                   VARCHAR(255) NOT NULL,
    address                 VARCHAR(255) NOT NULL,
    city                    VARCHAR(255) NOT NULL,
    province                VARCHAR(255) NOT NULL,
    postal_code             VARCHAR(255) NOT NULL,
    country                 VARCHAR(255) NOT NULL,
    business_number         VARCHAR(255) NULL,
    gst_number              VARCHAR(255) NULL,
    qst_number              VARCHAR(255) NULL,
    currency                VARCHAR(3)   NOT NULL,
    default_tax_province    VARCHAR(2)   NOT NULL,
    fiscal_year_start_month INT          NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_companies_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. app_users ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    company_id    BIGINT       NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    enabled       BIT(1)       NOT NULL DEFAULT 1,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_users_email (email),
    KEY idx_app_users_company (company_id),
    CONSTRAINT fk_app_users_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. customers ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    company_id      BIGINT       NOT NULL,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    business_name   VARCHAR(255) NOT NULL,
    address         VARCHAR(255) NOT NULL,
    city            VARCHAR(255) NOT NULL,
    province        VARCHAR(255) NOT NULL,
    postal_code     VARCHAR(255) NOT NULL,
    country         VARCHAR(255) NOT NULL,
    business_number VARCHAR(255) NULL,
    gst_number      VARCHAR(255) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customers_company_email (company_id, email),
    KEY idx_customers_company (company_id),
    CONSTRAINT fk_customers_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. chart_of_accounts ----------------------------------------------------
CREATE TABLE IF NOT EXISTS chart_of_accounts (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    company_id     BIGINT       NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    account_name   VARCHAR(255) NOT NULL,
    account_type   VARCHAR(32)  NOT NULL,
    description    VARCHAR(255) NOT NULL,
    is_active      BIT(1)       NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chart_of_accounts_company_number (company_id, account_number),
    KEY idx_chart_of_accounts_company (company_id),
    CONSTRAINT fk_chart_of_accounts_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. tax_agencies ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS tax_agencies (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    company_id     BIGINT       NOT NULL,
    code           VARCHAR(255) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    address        VARCHAR(500) NULL,
    website        VARCHAR(255) NULL,
    account_number VARCHAR(255) NULL,
    is_active      BIT(1)       NOT NULL DEFAULT 1,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tax_agencies_company_code (company_id, code),
    KEY idx_tax_agencies_company (company_id),
    CONSTRAINT fk_tax_agencies_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. invoices -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS invoices (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    company_id     BIGINT         NOT NULL,
    invoice_number VARCHAR(255)   NOT NULL,
    customer_id    BIGINT         NOT NULL,
    invoice_date   DATE           NOT NULL,
    due_date       DATE           NOT NULL,
    status         VARCHAR(32)    NOT NULL,
    subtotal       DECIMAL(19, 2) NOT NULL,
    gst_amount     DECIMAL(19, 2) NOT NULL,
    hst_amount     DECIMAL(19, 2) NOT NULL,
    qst_amount     DECIMAL(19, 2) NOT NULL,
    total_amount   DECIMAL(19, 2) NOT NULL,
    paid_amount    DECIMAL(19, 2) NULL,
    notes          VARCHAR(1000)  NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoices_company_number (company_id, invoice_number),
    KEY idx_invoices_company (company_id),
    KEY idx_invoices_customer (customer_id),
    KEY idx_invoices_status (status),
    KEY idx_invoices_date (invoice_date),
    CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_invoices_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. line_items -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS line_items (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    invoice_id  BIGINT         NOT NULL,
    description VARCHAR(255)   NOT NULL,
    quantity    DECIMAL(19, 2) NOT NULL,
    unit_price  DECIMAL(19, 2) NOT NULL,
    total       DECIMAL(19, 2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_line_items_invoice (invoice_id),
    CONSTRAINT fk_line_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. payments -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    company_id      BIGINT         NOT NULL,
    invoice_id      BIGINT         NOT NULL,
    bank_account_id BIGINT         NULL,
    amount          DECIMAL(19, 2) NOT NULL,
    payment_date    DATE           NOT NULL,
    payment_method  VARCHAR(32)    NOT NULL,
    status          VARCHAR(32)    NOT NULL,
    transaction_id  VARCHAR(255)   NOT NULL,
    notes           VARCHAR(500)   NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_payments_company (company_id),
    KEY idx_payments_invoice (invoice_id),
    KEY idx_payments_bank_account (bank_account_id),
    KEY idx_payments_status (status),
    KEY idx_payments_date (payment_date),
    CONSTRAINT fk_payments_invoice      FOREIGN KEY (invoice_id)      REFERENCES invoices          (id),
    CONSTRAINT fk_payments_bank_account FOREIGN KEY (bank_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_payments_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. general_journals -----------------------------------------------------
CREATE TABLE IF NOT EXISTS general_journals (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    company_id     BIGINT       NOT NULL,
    journal_number VARCHAR(255) NOT NULL,
    journal_date   DATE         NOT NULL,
    narrative      VARCHAR(255) NOT NULL,
    reference      VARCHAR(500) NULL,
    status         VARCHAR(32)  NOT NULL,
    posted_date    DATETIME(6)  NULL,
    posted_by      VARCHAR(500) NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_general_journals_company_number (company_id, journal_number),
    KEY idx_general_journals_company (company_id),
    KEY idx_general_journals_status (status),
    KEY idx_general_journals_date (journal_date),
    KEY idx_general_journals_reference (reference),
    CONSTRAINT fk_general_journals_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. journal_entries ------------------------------------------------------
CREATE TABLE IF NOT EXISTS journal_entries (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    journal_id  BIGINT         NOT NULL,
    account_id  BIGINT         NOT NULL,
    debit       DECIMAL(19, 2) NOT NULL,
    credit      DECIMAL(19, 2) NOT NULL,
    cleared     BOOLEAN        NOT NULL DEFAULT FALSE,
    description VARCHAR(500)   NULL,
    line_number INT            NOT NULL,
    PRIMARY KEY (id),
    KEY idx_journal_entries_journal (journal_id),
    KEY idx_journal_entries_account (account_id),
    CONSTRAINT fk_journal_entries_journal FOREIGN KEY (journal_id) REFERENCES general_journals (id) ON DELETE CASCADE,
    CONSTRAINT fk_journal_entries_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. general_ledger -------------------------------------------------------
CREATE TABLE IF NOT EXISTS general_ledger (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    company_id     BIGINT         NOT NULL,
    account_id     BIGINT         NOT NULL,
    debit_balance  DECIMAL(19, 2) NOT NULL,
    credit_balance DECIMAL(19, 2) NOT NULL,
    balance        DECIMAL(19, 2) NOT NULL,
    last_updated   DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_general_ledger_company (company_id),
    KEY idx_general_ledger_account (account_id),
    CONSTRAINT fk_general_ledger_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_general_ledger_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. tax_items -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS tax_items (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    company_id          BIGINT        NOT NULL,
    code                VARCHAR(255)  NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    rate                DECIMAL(7, 5) NOT NULL,
    agency_id           BIGINT        NOT NULL,
    payable_account_id  BIGINT        NULL,
    itc_account_id      BIGINT        NULL,
    for_sales           BIT(1)        NOT NULL,
    for_purchases       BIT(1)        NOT NULL,
    is_active           BIT(1)        NOT NULL DEFAULT 1,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tax_items_company_code (company_id, code),
    KEY idx_tax_items_company (company_id),
    KEY idx_tax_items_agency (agency_id),
    CONSTRAINT fk_tax_items_agency  FOREIGN KEY (agency_id)          REFERENCES tax_agencies      (id),
    CONSTRAINT fk_tax_items_payable FOREIGN KEY (payable_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_tax_items_itc     FOREIGN KEY (itc_account_id)     REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_tax_items_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10a. tax_groups ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS tax_groups (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    company_id  BIGINT       NOT NULL,
    code        VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    is_active   BIT(1)       NOT NULL DEFAULT 1,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tax_groups_company_code (company_id, code),
    KEY idx_tax_groups_company (company_id),
    CONSTRAINT fk_tax_groups_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10b. tax_group_items ----------------------------------------------------
CREATE TABLE IF NOT EXISTS tax_group_items (
    tax_group_id BIGINT NOT NULL,
    tax_item_id  BIGINT NOT NULL,
    KEY idx_tgi_group (tax_group_id),
    KEY idx_tgi_item (tax_item_id),
    CONSTRAINT fk_tgi_group FOREIGN KEY (tax_group_id) REFERENCES tax_groups (id),
    CONSTRAINT fk_tgi_item  FOREIGN KEY (tax_item_id)  REFERENCES tax_items  (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10c. tax_codes ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS tax_codes (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    company_id              BIGINT       NOT NULL,
    code                    VARCHAR(255) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    sales_tax_group_id      BIGINT       NULL,
    purchase_tax_group_id   BIGINT       NULL,
    is_active               BIT(1)       NOT NULL DEFAULT 1,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tax_codes_company_code (company_id, code),
    KEY idx_tax_codes_company (company_id),
    CONSTRAINT fk_tax_codes_sales_group    FOREIGN KEY (sales_tax_group_id)    REFERENCES tax_groups (id),
    CONSTRAINT fk_tax_codes_purchase_group FOREIGN KEY (purchase_tax_group_id) REFERENCES tax_groups (id),
    CONSTRAINT fk_tax_codes_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. tax_filing_periods --------------------------------------------------
CREATE TABLE IF NOT EXISTS tax_filing_periods (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    company_id          BIGINT         NOT NULL,
    agency_id           BIGINT         NOT NULL,
    period_start        DATE           NOT NULL,
    period_end          DATE           NOT NULL,
    status              VARCHAR(32)    NOT NULL,
    tax_collected       DECIMAL(19, 2) NOT NULL,
    tax_itc             DECIMAL(19, 2) NOT NULL,
    net_owing           DECIMAL(19, 2) NOT NULL,
    filed_date          DATE           NULL,
    paid_date           DATE           NULL,
    filing_journal_id   BIGINT         NULL,
    payment_journal_id  BIGINT         NULL,
    notes               VARCHAR(1000)  NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tax_filing_periods_company (company_id),
    KEY idx_tax_filing_periods_agency (agency_id),
    KEY idx_tax_filing_periods_status (status),
    CONSTRAINT fk_tax_filing_periods_agency          FOREIGN KEY (agency_id)          REFERENCES tax_agencies     (id),
    CONSTRAINT fk_tax_filing_periods_filing_journal  FOREIGN KEY (filing_journal_id)  REFERENCES general_journals (id),
    CONSTRAINT fk_tax_filing_periods_payment_journal FOREIGN KEY (payment_journal_id) REFERENCES general_journals (id),
    CONSTRAINT fk_tax_filing_periods_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. vendors -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vendors (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    company_id      BIGINT       NOT NULL,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    business_name   VARCHAR(255) NOT NULL,
    address         VARCHAR(255) NOT NULL,
    city            VARCHAR(255) NOT NULL,
    province        VARCHAR(255) NOT NULL,
    postal_code     VARCHAR(255) NOT NULL,
    country         VARCHAR(255) NOT NULL,
    business_number VARCHAR(255) NULL,
    gst_number      VARCHAR(255) NULL,
    qst_number      VARCHAR(255) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vendors_company_email (company_id, email),
    KEY idx_vendors_company (company_id),
    CONSTRAINT fk_vendors_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. bills ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bills (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    company_id   BIGINT         NOT NULL,
    bill_number  VARCHAR(255)   NOT NULL,
    vendor_id    BIGINT         NOT NULL,
    bill_date    DATE           NOT NULL,
    due_date     DATE           NOT NULL,
    status       VARCHAR(32)    NOT NULL,
    subtotal     DECIMAL(19, 2) NOT NULL,
    gst_amount   DECIMAL(19, 2) NOT NULL,
    hst_amount   DECIMAL(19, 2) NOT NULL,
    qst_amount   DECIMAL(19, 2) NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL,
    paid_amount  DECIMAL(19, 2) NULL,
    notes        VARCHAR(1000)  NULL,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bills_company_number (company_id, bill_number),
    KEY idx_bills_company (company_id),
    KEY idx_bills_vendor (vendor_id),
    KEY idx_bills_status (status),
    KEY idx_bills_date (bill_date),
    CONSTRAINT fk_bills_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id),
    CONSTRAINT fk_bills_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. bill_line_items -----------------------------------------------------
CREATE TABLE IF NOT EXISTS bill_line_items (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    bill_id            BIGINT         NOT NULL,
    expense_account_id BIGINT         NULL,
    description        VARCHAR(255)   NOT NULL,
    quantity           DECIMAL(19, 2) NOT NULL,
    unit_price         DECIMAL(19, 2) NOT NULL,
    total              DECIMAL(19, 2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_bill_line_items_bill (bill_id),
    KEY idx_bill_line_items_account (expense_account_id),
    CONSTRAINT fk_bill_line_items_bill    FOREIGN KEY (bill_id)            REFERENCES bills             (id) ON DELETE CASCADE,
    CONSTRAINT fk_bill_line_items_account FOREIGN KEY (expense_account_id) REFERENCES chart_of_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. bill_payments -------------------------------------------------------
CREATE TABLE IF NOT EXISTS bill_payments (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    company_id      BIGINT         NOT NULL,
    bill_id         BIGINT         NOT NULL,
    bank_account_id BIGINT         NULL,
    amount          DECIMAL(19, 2) NOT NULL,
    payment_date    DATE           NOT NULL,
    payment_method  VARCHAR(32)    NOT NULL,
    status          VARCHAR(32)    NOT NULL,
    transaction_id  VARCHAR(255)   NOT NULL,
    notes           VARCHAR(500)   NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_bill_payments_company (company_id),
    KEY idx_bill_payments_bill (bill_id),
    KEY idx_bill_payments_bank_account (bank_account_id),
    KEY idx_bill_payments_status (status),
    KEY idx_bill_payments_date (payment_date),
    CONSTRAINT fk_bill_payments_bill         FOREIGN KEY (bill_id)         REFERENCES bills             (id),
    CONSTRAINT fk_bill_payments_bank_account FOREIGN KEY (bank_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_bill_payments_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. fiscal_years --------------------------------------------------------
CREATE TABLE IF NOT EXISTS fiscal_years (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    company_id         BIGINT         NOT NULL,
    label              VARCHAR(255)   NOT NULL,
    start_date         DATE           NOT NULL,
    end_date           DATE           NOT NULL,
    status             VARCHAR(32)    NOT NULL,
    closing_journal_id BIGINT         NULL,
    closed_date        DATE           NULL,
    closed_by          VARCHAR(500)   NULL,
    net_income         DECIMAL(19, 2) NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fiscal_years_company_label (company_id, label),
    KEY idx_fiscal_years_company (company_id),
    KEY idx_fiscal_years_status (status),
    CONSTRAINT fk_fiscal_years_closing_journal FOREIGN KEY (closing_journal_id) REFERENCES general_journals (id),
    CONSTRAINT fk_fiscal_years_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 17. bank reconciliation -------------------------------------------------
-- Imported source rows are retained as company-owned records.  The unique
-- row hash prevents a statement row from being imported twice for an account.
CREATE TABLE IF NOT EXISTS bank_reconciliation_sessions (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    company_id        BIGINT         NOT NULL,
    bank_account_id   BIGINT         NOT NULL,
    statement_date    DATE           NOT NULL,
    opening_balance   DECIMAL(19, 2) NOT NULL,
    transaction_total DECIMAL(19, 2) NOT NULL,
    ending_balance    DECIMAL(19, 2) NOT NULL,
    completed_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_bank_reconciliation_sessions_company_account (company_id, bank_account_id),
    KEY idx_bank_reconciliation_sessions_statement_date (statement_date),
    CONSTRAINT fk_bank_reconciliation_sessions_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_bank_reconciliation_sessions_account FOREIGN KEY (bank_account_id) REFERENCES chart_of_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bank_transactions (
    id                        BIGINT         NOT NULL AUTO_INCREMENT,
    company_id                BIGINT         NOT NULL,
    bank_account_id           BIGINT         NOT NULL,
    transaction_date          DATE           NOT NULL,
    description               VARCHAR(1000)  NOT NULL,
    amount                    DECIMAL(19, 2) NOT NULL,
    reference                 VARCHAR(500)   NULL,
    source_hash               VARCHAR(64)    NOT NULL,
    source_row_hash           VARCHAR(64)    NOT NULL,
    reconciled                BOOLEAN        NOT NULL DEFAULT FALSE,
    reconciliation_session_id BIGINT         NULL,
    created_at                DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bank_transactions_company_account_row (company_id, bank_account_id, source_row_hash),
    KEY idx_bank_transactions_company_account_status (company_id, bank_account_id, reconciled),
    KEY idx_bank_transactions_source_hash (company_id, bank_account_id, source_hash),
    KEY idx_bank_transactions_session (reconciliation_session_id),
    CONSTRAINT fk_bank_transactions_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_bank_transactions_account FOREIGN KEY (bank_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_bank_transactions_session FOREIGN KEY (reconciliation_session_id)
        REFERENCES bank_reconciliation_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================================
-- Schema Updates (Migrations)
-- =========================================================================

-- Add is_active column to tax tables if they don't exist (MySQL 8.0.19+)
-- Note: Using a stored procedure for idempotency on older MySQL versions if needed,
-- but for simplicity we provide the ALTER statements.

SET @dropdown_query = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_agencies' AND COLUMN_NAME = 'is_active') > 0,
    'SELECT 1',
    'ALTER TABLE tax_agencies ADD COLUMN is_active BIT(1) NOT NULL DEFAULT 1 AFTER account_number'
));
PREPARE stmt FROM @dropdown_query;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Chart-of-account attributes used by company-scoped bank reconciliation
-- were added after the original table definition.  Each update only adds a
-- missing nullable column, preserving existing charts and their data.
SET @account_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chart_of_accounts' AND COLUMN_NAME = 'category') > 0,
    'SELECT 1', 'ALTER TABLE chart_of_accounts ADD COLUMN category VARCHAR(40) NULL'));
PREPARE stmt FROM @account_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @account_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chart_of_accounts' AND COLUMN_NAME = 'parent_account_id') > 0,
    'SELECT 1', 'ALTER TABLE chart_of_accounts ADD COLUMN parent_account_id BIGINT NULL'));
PREPARE stmt FROM @account_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @account_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chart_of_accounts' AND COLUMN_NAME = 'currency') > 0,
    'SELECT 1', 'ALTER TABLE chart_of_accounts ADD COLUMN currency VARCHAR(255) NULL'));
PREPARE stmt FROM @account_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @account_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chart_of_accounts' AND COLUMN_NAME = 'opening_balance') > 0,
    'SELECT 1', 'ALTER TABLE chart_of_accounts ADD COLUMN opening_balance DECIMAL(19, 2) NULL'));
PREPARE stmt FROM @account_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @account_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chart_of_accounts' AND COLUMN_NAME = 'opening_balance_date') > 0,
    'SELECT 1', 'ALTER TABLE chart_of_accounts ADD COLUMN opening_balance_date DATE NULL'));
PREPARE stmt FROM @account_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Optional transaction roots introduced after the original schema.  These
-- definitions are idempotent and never replace existing data.
CREATE TABLE IF NOT EXISTS written_cheques (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    cheque_number VARCHAR(255) NOT NULL,
    cheque_date DATE NOT NULL,
    vendor_id BIGINT NOT NULL,
    bank_account_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    memo VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    voided_at DATETIME NULL,
    void_reason VARCHAR(500) NULL,
    reissue_of_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_written_cheques_company_number (company_id, cheque_number),
    KEY idx_written_cheques_company (company_id),
    CONSTRAINT fk_written_cheques_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_written_cheques_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id),
    CONSTRAINT fk_written_cheques_bank FOREIGN KEY (bank_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_written_cheques_reissue FOREIGN KEY (reissue_of_id) REFERENCES written_cheques (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @cheque_status_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'written_cheques' AND COLUMN_NAME = 'status') > 0,
    'SELECT 1',
    'ALTER TABLE written_cheques ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT ''ISSUED'''));
PREPARE stmt FROM @cheque_status_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE written_cheques MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DRAFT';

SET @cheque_voided_at_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'written_cheques' AND COLUMN_NAME = 'voided_at') > 0,
    'SELECT 1', 'ALTER TABLE written_cheques ADD COLUMN voided_at DATETIME NULL'));
PREPARE stmt FROM @cheque_voided_at_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @cheque_void_reason_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'written_cheques' AND COLUMN_NAME = 'void_reason') > 0,
    'SELECT 1', 'ALTER TABLE written_cheques ADD COLUMN void_reason VARCHAR(500) NULL'));
PREPARE stmt FROM @cheque_void_reason_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @cheque_reissue_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'written_cheques' AND COLUMN_NAME = 'reissue_of_id') > 0,
    'SELECT 1', 'ALTER TABLE written_cheques ADD COLUMN reissue_of_id BIGINT NULL'));
PREPARE stmt FROM @cheque_reissue_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @cheque_reissue_fk_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'written_cheques' AND CONSTRAINT_NAME = 'fk_written_cheques_reissue') > 0,
    'SELECT 1',
    'ALTER TABLE written_cheques ADD CONSTRAINT fk_written_cheques_reissue FOREIGN KEY (reissue_of_id) REFERENCES written_cheques (id)'));
PREPARE stmt FROM @cheque_reissue_fk_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS cheque_expenses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cheque_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    customer_job_id BIGINT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    memo VARCHAR(500) NULL,
    tax VARCHAR(40) NULL,
    PRIMARY KEY (id),
    KEY idx_cheque_expenses_cheque (cheque_id),
    CONSTRAINT fk_cheque_expenses_cheque FOREIGN KEY (cheque_id) REFERENCES written_cheques (id) ON DELETE CASCADE,
    CONSTRAINT fk_cheque_expenses_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_cheque_expenses_customer FOREIGN KEY (customer_job_id) REFERENCES customers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @cheque_expense_tax_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cheque_expenses' AND COLUMN_NAME = 'tax') > 0,
    'SELECT 1', 'ALTER TABLE cheque_expenses ADD COLUMN tax VARCHAR(40) NULL'));
PREPARE stmt FROM @cheque_expense_tax_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS credit_card_charges (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    vendor_id BIGINT NOT NULL,
    charge_date DATE NOT NULL,
    memo VARCHAR(1000) NULL,
    expense_account_id BIGINT NOT NULL,
    card_account_id BIGINT NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL,
    net_amount DECIMAL(19, 2) NOT NULL,
    tax_amount DECIMAL(19, 2) NOT NULL,
    tax_regime VARCHAR(30) NOT NULL,
    journal_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    voided_at DATETIME NULL,
    void_reason VARCHAR(500) NULL,
    reissue_of_id BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_credit_card_charges_company (company_id),
    CONSTRAINT fk_credit_card_charges_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_credit_card_charges_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id),
    CONSTRAINT fk_credit_card_charges_expense FOREIGN KEY (expense_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_credit_card_charges_card FOREIGN KEY (card_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_credit_card_charges_journal FOREIGN KEY (journal_id) REFERENCES general_journals (id),
    CONSTRAINT fk_credit_card_charges_reissue FOREIGN KEY (reissue_of_id) REFERENCES credit_card_charges (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @card_charge_status_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'credit_card_charges' AND COLUMN_NAME = 'status') > 0,
    'SELECT 1',
    'ALTER TABLE credit_card_charges ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT ''POSTED'''));
PREPARE stmt FROM @card_charge_status_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE credit_card_charges MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DRAFT';

SET @card_charge_voided_at_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'credit_card_charges' AND COLUMN_NAME = 'voided_at') > 0,
    'SELECT 1', 'ALTER TABLE credit_card_charges ADD COLUMN voided_at DATETIME NULL'));
PREPARE stmt FROM @card_charge_voided_at_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @card_charge_void_reason_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'credit_card_charges' AND COLUMN_NAME = 'void_reason') > 0,
    'SELECT 1', 'ALTER TABLE credit_card_charges ADD COLUMN void_reason VARCHAR(500) NULL'));
PREPARE stmt FROM @card_charge_void_reason_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @card_charge_reissue_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'credit_card_charges' AND COLUMN_NAME = 'reissue_of_id') > 0,
    'SELECT 1', 'ALTER TABLE credit_card_charges ADD COLUMN reissue_of_id BIGINT NULL'));
PREPARE stmt FROM @card_charge_reissue_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @card_charge_reissue_fk_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'credit_card_charges' AND CONSTRAINT_NAME = 'fk_credit_card_charges_reissue') > 0,
    'SELECT 1',
    'ALTER TABLE credit_card_charges ADD CONSTRAINT fk_credit_card_charges_reissue FOREIGN KEY (reissue_of_id) REFERENCES credit_card_charges (id)'));
PREPARE stmt FROM @card_charge_reissue_fk_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS transfers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    from_account_id BIGINT NOT NULL,
    to_account_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    transfer_date DATE NULL,
    notes VARCHAR(1000) NULL,
    journal_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    voided_at DATETIME NULL,
    void_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_transfers_company (company_id),
    CONSTRAINT fk_transfers_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_transfers_from_account FOREIGN KEY (from_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_transfers_to_account FOREIGN KEY (to_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_transfers_journal FOREIGN KEY (journal_id) REFERENCES general_journals (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @transfer_status_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'transfers' AND COLUMN_NAME = 'status') > 0,
    'SELECT 1',
    'ALTER TABLE transfers ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT ''POSTED'''));
PREPARE stmt FROM @transfer_status_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE transfers MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DRAFT';

SET @transfer_voided_at_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'transfers' AND COLUMN_NAME = 'voided_at') > 0,
    'SELECT 1', 'ALTER TABLE transfers ADD COLUMN voided_at DATETIME NULL'));
PREPARE stmt FROM @transfer_voided_at_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @transfer_void_reason_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'transfers' AND COLUMN_NAME = 'void_reason') > 0,
    'SELECT 1', 'ALTER TABLE transfers ADD COLUMN void_reason VARCHAR(500) NULL'));
PREPARE stmt FROM @transfer_void_reason_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Protected invoice and bill attachments. Metadata and file content are
-- deliberately separate so list operations never retrieve binary content.
CREATE TABLE IF NOT EXISTS document_attachments (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    company_id     BIGINT        NOT NULL,
    invoice_id     BIGINT        NULL,
    bill_id        BIGINT        NULL,
    filename       VARCHAR(255)  NOT NULL,
    content_type   VARCHAR(100)  NOT NULL,
    content_length BIGINT        NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_document_attachments_company (company_id),
    KEY idx_document_attachments_invoice (invoice_id),
    KEY idx_document_attachments_bill (bill_id),
    CONSTRAINT chk_document_attachment_owner CHECK (
        (invoice_id IS NOT NULL AND bill_id IS NULL)
        OR (invoice_id IS NULL AND bill_id IS NOT NULL)
    ),
    CONSTRAINT fk_document_attachments_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_document_attachments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_attachments_bill FOREIGN KEY (bill_id) REFERENCES bills (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS document_attachment_contents (
    attachment_id BIGINT    NOT NULL,
    content       LONGBLOB  NOT NULL,
    PRIMARY KEY (attachment_id),
    CONSTRAINT fk_document_attachment_contents_attachment FOREIGN KEY (attachment_id)
        REFERENCES document_attachments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing installations are upgraded non-destructively.  Company columns
-- intentionally begin nullable; CompanyOwnershipMigration backfills them once
-- AuthBootstrap has established the bootstrap company, then makes them NOT NULL.
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE app_users ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customers' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE customers ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendors' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE vendors ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chart_of_accounts' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE chart_of_accounts ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'invoices' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE invoices ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bills' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE bills ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE payments ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bill_payments' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE bill_payments ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'general_journals' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE general_journals ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'general_ledger' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE general_ledger ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_agencies' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE tax_agencies ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_items' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE tax_items ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_groups' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE tax_groups ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_codes' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE tax_codes ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_filing_periods' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE tax_filing_periods ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fiscal_years' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE fiscal_years ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'written_cheques' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE written_cheques ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'credit_card_charges' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE credit_card_charges ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @company_column_sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'transfers' AND COLUMN_NAME = 'company_id') > 0,
    'SELECT 1', 'ALTER TABLE transfers ADD COLUMN company_id BIGINT NULL'));
PREPARE stmt FROM @company_column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @dropdown_query = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_items' AND COLUMN_NAME = 'is_active') > 0,
    'SELECT 1',
    'ALTER TABLE tax_items ADD COLUMN is_active BIT(1) NOT NULL DEFAULT 1 AFTER for_purchases'
));
PREPARE stmt FROM @dropdown_query;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dropdown_query = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_groups' AND COLUMN_NAME = 'is_active') > 0,
    'SELECT 1',
    'ALTER TABLE tax_groups ADD COLUMN is_active BIT(1) NOT NULL DEFAULT 1 AFTER name'
));
PREPARE stmt FROM @dropdown_query;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dropdown_query = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tax_codes' AND COLUMN_NAME = 'is_active') > 0,
    'SELECT 1',
    'ALTER TABLE tax_codes ADD COLUMN is_active BIT(1) NOT NULL DEFAULT 1 AFTER purchase_tax_group_id'
));
PREPARE stmt FROM @dropdown_query;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
