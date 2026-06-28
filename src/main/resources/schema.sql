-- =========================================================================
-- Accounting schema — MySQL DDL
-- Idempotent: safe to re-run on every startup
-- =========================================================================

-- 1. customers ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
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
    UNIQUE KEY uk_customers_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. chart_of_accounts ----------------------------------------------------
CREATE TABLE IF NOT EXISTS chart_of_accounts (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    account_number VARCHAR(255) NOT NULL,
    account_name   VARCHAR(255) NOT NULL,
    account_type   VARCHAR(32)  NOT NULL,
    description    VARCHAR(255) NOT NULL,
    is_active      BIT(1)       NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chart_of_accounts_number (account_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. tax_agencies ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS tax_agencies (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    code           VARCHAR(255) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    address        VARCHAR(500) NULL,
    website        VARCHAR(255) NULL,
    account_number VARCHAR(255) NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tax_agencies_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. invoices -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS invoices (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
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
    UNIQUE KEY uk_invoices_number (invoice_number),
    KEY idx_invoices_customer (customer_id),
    KEY idx_invoices_status (status),
    KEY idx_invoices_date (invoice_date),
    CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. line_items -----------------------------------------------------------
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
    KEY idx_payments_invoice (invoice_id),
    KEY idx_payments_bank_account (bank_account_id),
    KEY idx_payments_status (status),
    KEY idx_payments_date (payment_date),
    CONSTRAINT fk_payments_invoice      FOREIGN KEY (invoice_id)      REFERENCES invoices          (id),
    CONSTRAINT fk_payments_bank_account FOREIGN KEY (bank_account_id) REFERENCES chart_of_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. general_journals -----------------------------------------------------
CREATE TABLE IF NOT EXISTS general_journals (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
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
    UNIQUE KEY uk_general_journals_number (journal_number),
    KEY idx_general_journals_status (status),
    KEY idx_general_journals_date (journal_date),
    KEY idx_general_journals_reference (reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. journal_entries ------------------------------------------------------
CREATE TABLE IF NOT EXISTS journal_entries (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    journal_id  BIGINT         NOT NULL,
    account_id  BIGINT         NOT NULL,
    debit       DECIMAL(19, 2) NOT NULL,
    credit      DECIMAL(19, 2) NOT NULL,
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
    account_id     BIGINT         NOT NULL,
    debit_balance  DECIMAL(19, 2) NOT NULL,
    credit_balance DECIMAL(19, 2) NOT NULL,
    balance        DECIMAL(19, 2) NOT NULL,
    last_updated   DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_general_ledger_account (account_id),
    CONSTRAINT fk_general_ledger_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. tax_codes -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS tax_codes (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    code                VARCHAR(255)  NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    rate                DECIMAL(7, 5) NOT NULL,
    agency_id           BIGINT        NOT NULL,
    payable_account_id  BIGINT        NULL,
    itc_account_id      BIGINT        NULL,
    is_active           BIT(1)        NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tax_codes_code (code),
    KEY idx_tax_codes_agency (agency_id),
    CONSTRAINT fk_tax_codes_agency  FOREIGN KEY (agency_id)          REFERENCES tax_agencies      (id),
    CONSTRAINT fk_tax_codes_payable FOREIGN KEY (payable_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_tax_codes_itc     FOREIGN KEY (itc_account_id)     REFERENCES chart_of_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. tax_filing_periods --------------------------------------------------
CREATE TABLE IF NOT EXISTS tax_filing_periods (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
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
    KEY idx_tax_filing_periods_agency (agency_id),
    KEY idx_tax_filing_periods_status (status),
    CONSTRAINT fk_tax_filing_periods_agency          FOREIGN KEY (agency_id)          REFERENCES tax_agencies     (id),
    CONSTRAINT fk_tax_filing_periods_filing_journal  FOREIGN KEY (filing_journal_id)  REFERENCES general_journals (id),
    CONSTRAINT fk_tax_filing_periods_payment_journal FOREIGN KEY (payment_journal_id) REFERENCES general_journals (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. vendors -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vendors (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
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
    UNIQUE KEY uk_vendors_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. bills ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bills (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
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
    UNIQUE KEY uk_bills_number (bill_number),
    KEY idx_bills_vendor (vendor_id),
    KEY idx_bills_status (status),
    KEY idx_bills_date (bill_date),
    CONSTRAINT fk_bills_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id)
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
    KEY idx_bill_payments_bill (bill_id),
    KEY idx_bill_payments_bank_account (bank_account_id),
    KEY idx_bill_payments_status (status),
    KEY idx_bill_payments_date (payment_date),
    CONSTRAINT fk_bill_payments_bill         FOREIGN KEY (bill_id)         REFERENCES bills             (id),
    CONSTRAINT fk_bill_payments_bank_account FOREIGN KEY (bank_account_id) REFERENCES chart_of_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. fiscal_years --------------------------------------------------------
CREATE TABLE IF NOT EXISTS fiscal_years (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
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
    UNIQUE KEY uk_fiscal_years_label (label),
    KEY idx_fiscal_years_status (status),
    CONSTRAINT fk_fiscal_years_closing_journal FOREIGN KEY (closing_journal_id) REFERENCES general_journals (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
