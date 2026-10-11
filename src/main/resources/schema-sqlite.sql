-- SQLite schema. Keep aligned with the MySQL schema and entity mappings.
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS companies (

    id                      INTEGER NOT NULL,
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
    posted_journal_editing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              DATETIME  NOT NULL,
    updated_at              DATETIME  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_companies_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS app_users (

    id            INTEGER NOT NULL,
    company_id    BIGINT       NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    short_id      VARCHAR(7),
    role          VARCHAR(32)  NOT NULL,
    enabled       BOOLEAN       NOT NULL DEFAULT 1,
    created_at    DATETIME  NOT NULL,
    updated_at    DATETIME  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT fk_app_users_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_app_users_company ON app_users (company_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_app_users_short_id ON app_users (short_id);

CREATE TABLE IF NOT EXISTS user_company_memberships (

    user_id    BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    role       VARCHAR(32) NOT NULL DEFAULT 'AUDITOR',
    PRIMARY KEY (user_id, company_id),
    CONSTRAINT fk_user_company_memberships_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_company_memberships_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_user_company_memberships_company ON user_company_memberships (company_id);

CREATE TABLE IF NOT EXISTS customers (

    id              INTEGER NOT NULL,
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
    created_at      DATETIME  NOT NULL,
    updated_at      DATETIME  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_customers_company_email UNIQUE (company_id, email),
    CONSTRAINT fk_customers_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_customers_company ON customers (company_id);

CREATE TABLE IF NOT EXISTS chart_of_accounts (

    id             INTEGER NOT NULL,
    company_id     BIGINT       NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    account_name   VARCHAR(255) NOT NULL,
    account_type   VARCHAR(32)  NOT NULL,
    description    VARCHAR(255) NOT NULL,
    is_active      BOOLEAN       NOT NULL,
    created_at     DATETIME  NOT NULL,
    updated_at     DATETIME  NOT NULL,
    category VARCHAR(40) NULL,
    parent_account_id BIGINT NULL,
    currency VARCHAR(255) NULL,
    opening_balance DECIMAL(19, 2) NULL,
    opening_balance_date DATE NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chart_of_accounts_company_number UNIQUE (company_id, account_number),
    CONSTRAINT fk_chart_of_accounts_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_chart_of_accounts_company ON chart_of_accounts (company_id);

CREATE TABLE IF NOT EXISTS tax_agencies (

    id             INTEGER NOT NULL,
    company_id     BIGINT       NOT NULL,
    code           VARCHAR(255) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    address        VARCHAR(500) NULL,
    website        VARCHAR(255) NULL,
    account_number VARCHAR(255) NULL,
    is_active      BOOLEAN       NOT NULL DEFAULT 1,
    created_at     DATETIME  NOT NULL,
    updated_at     DATETIME  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tax_agencies_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_tax_agencies_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_tax_agencies_company ON tax_agencies (company_id);

CREATE TABLE IF NOT EXISTS invoices (

    id             INTEGER NOT NULL,
    company_id     BIGINT         NOT NULL,
    invoice_number VARCHAR(255)   NOT NULL,
    customer_id    BIGINT         NOT NULL,
    invoice_date   DATE           NOT NULL,
    due_date       DATE           NOT NULL,
    status         VARCHAR(32)    NOT NULL,
    tax_regime     VARCHAR(30)    NULL,
    subtotal       DECIMAL(19, 2) NOT NULL,
    gst_amount     DECIMAL(19, 2) NOT NULL,
    hst_amount     DECIMAL(19, 2) NOT NULL,
    qst_amount     DECIMAL(19, 2) NOT NULL,
    total_amount   DECIMAL(19, 2) NOT NULL,
    paid_amount    DECIMAL(19, 2) NULL,
    opening_paid_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    notes          VARCHAR(1000)  NULL,
    created_at     DATETIME    NOT NULL,
    updated_at     DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_invoices_company_number UNIQUE (company_id, invoice_number),
    CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_invoices_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_invoices_company ON invoices (company_id);
CREATE INDEX IF NOT EXISTS idx_invoices_customer ON invoices (customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices (status);
CREATE INDEX IF NOT EXISTS idx_invoices_date ON invoices (invoice_date);

CREATE TABLE IF NOT EXISTS line_items (

    id          INTEGER NOT NULL,
    invoice_id  BIGINT         NOT NULL,
    description VARCHAR(255)   NOT NULL,
    quantity    DECIMAL(19, 2) NOT NULL,
    unit_price  DECIMAL(19, 2) NOT NULL,
    total       DECIMAL(19, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_line_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_line_items_invoice ON line_items (invoice_id);

CREATE TABLE IF NOT EXISTS payments (

    id              INTEGER NOT NULL,
    company_id      BIGINT         NOT NULL,
    invoice_id      BIGINT         NOT NULL,
    bank_account_id BIGINT         NULL,
    amount          DECIMAL(19, 2) NOT NULL,
    payment_date    DATE           NOT NULL,
    payment_method  VARCHAR(32)    NOT NULL,
    status          VARCHAR(32)    NOT NULL,
    transaction_id  VARCHAR(255)   NOT NULL,
    notes           VARCHAR(500)   NULL,
    created_at      DATETIME    NOT NULL,
    updated_at      DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payments_invoice      FOREIGN KEY (invoice_id)      REFERENCES invoices          (id),
    CONSTRAINT fk_payments_bank_account FOREIGN KEY (bank_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_payments_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_payments_company ON payments (company_id);
CREATE INDEX IF NOT EXISTS idx_payments_invoice ON payments (invoice_id);
CREATE INDEX IF NOT EXISTS idx_payments_bank_account ON payments (bank_account_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments (status);
CREATE INDEX IF NOT EXISTS idx_payments_date ON payments (payment_date);

CREATE TABLE IF NOT EXISTS general_journals (

    id             INTEGER NOT NULL,
    company_id     BIGINT       NOT NULL,
    journal_number VARCHAR(255) NOT NULL,
    journal_date   DATE         NOT NULL,
    narrative      VARCHAR(255) NOT NULL,
    reference      VARCHAR(500) NULL,
    status         VARCHAR(32)  NOT NULL,
    posted_date    DATETIME  NULL,
    posted_by      VARCHAR(500) NULL,
    created_at     DATETIME  NOT NULL,
    updated_at     DATETIME  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_general_journals_company_number UNIQUE (company_id, journal_number),
    CONSTRAINT fk_general_journals_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_general_journals_company ON general_journals (company_id);
CREATE INDEX IF NOT EXISTS idx_general_journals_status ON general_journals (status);
CREATE INDEX IF NOT EXISTS idx_general_journals_date ON general_journals (journal_date);
CREATE INDEX IF NOT EXISTS idx_general_journals_reference ON general_journals (reference);

CREATE TABLE IF NOT EXISTS journal_entries (

    id          INTEGER NOT NULL,
    journal_id  BIGINT         NOT NULL,
    account_id  BIGINT         NOT NULL,
    customer_id BIGINT         NULL,
    vendor_id   BIGINT         NULL,
    tax_agency_id BIGINT       NULL,
    tax_item_id BIGINT         NULL,
    debit       DECIMAL(19, 2) NOT NULL,
    credit      DECIMAL(19, 2) NOT NULL,
    cleared     BOOLEAN        NOT NULL DEFAULT FALSE,
    description VARCHAR(500)   NULL,
    line_number INT            NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_journal_entries_journal FOREIGN KEY (journal_id) REFERENCES general_journals (id) ON DELETE CASCADE,
    CONSTRAINT fk_journal_entries_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts (id)
);
CREATE INDEX IF NOT EXISTS idx_journal_entries_journal ON journal_entries (journal_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_account ON journal_entries (account_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_customer ON journal_entries (customer_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_vendor ON journal_entries (vendor_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_tax_agency ON journal_entries (tax_agency_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_tax_item ON journal_entries (tax_item_id);

CREATE TABLE IF NOT EXISTS general_ledger (

    id             INTEGER NOT NULL,
    company_id     BIGINT         NOT NULL,
    account_id     BIGINT         NOT NULL,
    debit_balance  DECIMAL(19, 2) NOT NULL,
    credit_balance DECIMAL(19, 2) NOT NULL,
    balance        DECIMAL(19, 2) NOT NULL,
    last_updated   DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_general_ledger_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_general_ledger_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_general_ledger_company ON general_ledger (company_id);
CREATE INDEX IF NOT EXISTS idx_general_ledger_account ON general_ledger (account_id);

CREATE TABLE IF NOT EXISTS tax_items (

    id                  INTEGER NOT NULL,
    company_id          BIGINT        NOT NULL,
    code                VARCHAR(255)  NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    rate                DECIMAL(7, 5) NOT NULL,
    agency_id           BIGINT        NOT NULL,
    payable_account_id  BIGINT        NULL,
    itc_account_id      BIGINT        NULL,
    for_sales           BOOLEAN        NOT NULL,
    for_purchases       BOOLEAN        NOT NULL,
    sales_return_line   VARCHAR(20)   NULL,
    purchase_return_line VARCHAR(20)  NULL,
    is_active           BOOLEAN        NOT NULL DEFAULT 1,
    created_at          DATETIME   NOT NULL,
    updated_at          DATETIME   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tax_items_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_tax_items_agency  FOREIGN KEY (agency_id)          REFERENCES tax_agencies      (id),
    CONSTRAINT fk_tax_items_payable FOREIGN KEY (payable_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_tax_items_itc     FOREIGN KEY (itc_account_id)     REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_tax_items_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_tax_items_company ON tax_items (company_id);
CREATE INDEX IF NOT EXISTS idx_tax_items_agency ON tax_items (agency_id);

CREATE TABLE IF NOT EXISTS tax_groups (

    id          INTEGER NOT NULL,
    company_id  BIGINT       NOT NULL,
    code        VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    is_active   BOOLEAN       NOT NULL DEFAULT 1,
    created_at  DATETIME  NOT NULL,
    updated_at  DATETIME  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tax_groups_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_tax_groups_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_tax_groups_company ON tax_groups (company_id);

CREATE TABLE IF NOT EXISTS tax_group_items (

    tax_group_id BIGINT NOT NULL,
    tax_item_id  BIGINT NOT NULL,
    CONSTRAINT fk_tgi_group FOREIGN KEY (tax_group_id) REFERENCES tax_groups (id),
    CONSTRAINT fk_tgi_item  FOREIGN KEY (tax_item_id)  REFERENCES tax_items  (id)
);
CREATE INDEX IF NOT EXISTS idx_tgi_group ON tax_group_items (tax_group_id);
CREATE INDEX IF NOT EXISTS idx_tgi_item ON tax_group_items (tax_item_id);

CREATE TABLE IF NOT EXISTS tax_codes (

    id                      INTEGER NOT NULL,
    company_id              BIGINT       NOT NULL,
    code                    VARCHAR(255) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    sales_tax_group_id      BIGINT       NULL,
    purchase_tax_group_id   BIGINT       NULL,
    is_active               BOOLEAN       NOT NULL DEFAULT 1,
    created_at              DATETIME  NOT NULL,
    updated_at              DATETIME  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tax_codes_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_tax_codes_sales_group    FOREIGN KEY (sales_tax_group_id)    REFERENCES tax_groups (id),
    CONSTRAINT fk_tax_codes_purchase_group FOREIGN KEY (purchase_tax_group_id) REFERENCES tax_groups (id),
    CONSTRAINT fk_tax_codes_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_tax_codes_company ON tax_codes (company_id);

CREATE TABLE IF NOT EXISTS tax_filing_periods (

    id                  INTEGER NOT NULL,
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
    created_at          DATETIME    NOT NULL,
    updated_at          DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tax_filing_periods_agency          FOREIGN KEY (agency_id)          REFERENCES tax_agencies     (id),
    CONSTRAINT fk_tax_filing_periods_filing_journal  FOREIGN KEY (filing_journal_id)  REFERENCES general_journals (id),
    CONSTRAINT fk_tax_filing_periods_payment_journal FOREIGN KEY (payment_journal_id) REFERENCES general_journals (id),
    CONSTRAINT fk_tax_filing_periods_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_tax_filing_periods_company ON tax_filing_periods (company_id);
CREATE INDEX IF NOT EXISTS idx_tax_filing_periods_agency ON tax_filing_periods (agency_id);
CREATE INDEX IF NOT EXISTS idx_tax_filing_periods_status ON tax_filing_periods (status);

CREATE TABLE IF NOT EXISTS tax_return_row_snapshots (

    id              INTEGER NOT NULL,
    period_id       BIGINT         NOT NULL,
    display_order   INT            NOT NULL,
    description_key VARCHAR(200)   NOT NULL,
    line            VARCHAR(20)    NULL,
    amount          DECIMAL(19, 2) NULL,
    balance         DECIMAL(19, 2) NULL,
    total           BOOLEAN        NOT NULL,
    unmapped        BOOLEAN        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tax_return_snapshot_period_order UNIQUE (period_id, display_order),
    CONSTRAINT fk_tax_return_snapshot_period FOREIGN KEY (period_id)
        REFERENCES tax_filing_periods (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS vendors (

    id              INTEGER NOT NULL,
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
    created_at      DATETIME  NOT NULL,
    updated_at      DATETIME  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_vendors_company_email UNIQUE (company_id, email),
    CONSTRAINT fk_vendors_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_vendors_company ON vendors (company_id);

CREATE TABLE IF NOT EXISTS bills (

    id           INTEGER NOT NULL,
    company_id   BIGINT         NOT NULL,
    bill_number  VARCHAR(255)   NOT NULL,
    vendor_id    BIGINT         NOT NULL,
    bill_date    DATE           NOT NULL,
    due_date     DATE           NOT NULL,
    status       VARCHAR(32)    NOT NULL,
    tax_regime   VARCHAR(30)    NULL,
    subtotal     DECIMAL(19, 2) NOT NULL,
    gst_amount   DECIMAL(19, 2) NOT NULL,
    hst_amount   DECIMAL(19, 2) NOT NULL,
    qst_amount   DECIMAL(19, 2) NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL,
    paid_amount  DECIMAL(19, 2) NULL,
    notes        VARCHAR(1000)  NULL,
    created_at   DATETIME    NOT NULL,
    updated_at   DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bills_company_number UNIQUE (company_id, bill_number),
    CONSTRAINT fk_bills_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id),
    CONSTRAINT fk_bills_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_bills_company ON bills (company_id);
CREATE INDEX IF NOT EXISTS idx_bills_vendor ON bills (vendor_id);
CREATE INDEX IF NOT EXISTS idx_bills_status ON bills (status);
CREATE INDEX IF NOT EXISTS idx_bills_date ON bills (bill_date);

CREATE TABLE IF NOT EXISTS bill_line_items (

    id                 INTEGER NOT NULL,
    bill_id            BIGINT         NOT NULL,
    expense_account_id BIGINT         NULL,
    description        VARCHAR(255)   NOT NULL,
    quantity           DECIMAL(19, 2) NOT NULL,
    unit_price         DECIMAL(19, 2) NOT NULL,
    total              DECIMAL(19, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bill_line_items_bill    FOREIGN KEY (bill_id)            REFERENCES bills             (id) ON DELETE CASCADE,
    CONSTRAINT fk_bill_line_items_account FOREIGN KEY (expense_account_id) REFERENCES chart_of_accounts (id)
);
CREATE INDEX IF NOT EXISTS idx_bill_line_items_bill ON bill_line_items (bill_id);
CREATE INDEX IF NOT EXISTS idx_bill_line_items_account ON bill_line_items (expense_account_id);

CREATE TABLE IF NOT EXISTS bill_payments (

    id              INTEGER NOT NULL,
    company_id      BIGINT         NOT NULL,
    bill_id         BIGINT         NOT NULL,
    bank_account_id BIGINT         NULL,
    amount          DECIMAL(19, 2) NOT NULL,
    payment_date    DATE           NOT NULL,
    payment_method  VARCHAR(32)    NOT NULL,
    status          VARCHAR(32)    NOT NULL,
    transaction_id  VARCHAR(255)   NOT NULL,
    notes           VARCHAR(500)   NULL,
    created_at      DATETIME    NOT NULL,
    updated_at      DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bill_payments_bill         FOREIGN KEY (bill_id)         REFERENCES bills             (id),
    CONSTRAINT fk_bill_payments_bank_account FOREIGN KEY (bank_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_bill_payments_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_bill_payments_company ON bill_payments (company_id);
CREATE INDEX IF NOT EXISTS idx_bill_payments_bill ON bill_payments (bill_id);
CREATE INDEX IF NOT EXISTS idx_bill_payments_bank_account ON bill_payments (bank_account_id);
CREATE INDEX IF NOT EXISTS idx_bill_payments_status ON bill_payments (status);
CREATE INDEX IF NOT EXISTS idx_bill_payments_date ON bill_payments (payment_date);

CREATE TABLE IF NOT EXISTS fiscal_years (

    id                 INTEGER NOT NULL,
    company_id         BIGINT         NOT NULL,
    label              VARCHAR(255)   NOT NULL,
    start_date         DATE           NOT NULL,
    end_date           DATE           NOT NULL,
    status             VARCHAR(32)    NOT NULL,
    closing_journal_id BIGINT         NULL,
    closed_date        DATE           NULL,
    closed_by          VARCHAR(500)   NULL,
    net_income         DECIMAL(19, 2) NULL,
    created_at         DATETIME    NOT NULL,
    updated_at         DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fiscal_years_company_label UNIQUE (company_id, label),
    CONSTRAINT fk_fiscal_years_closing_journal FOREIGN KEY (closing_journal_id) REFERENCES general_journals (id),
    CONSTRAINT fk_fiscal_years_company FOREIGN KEY (company_id) REFERENCES companies (id)
);
CREATE INDEX IF NOT EXISTS idx_fiscal_years_company ON fiscal_years (company_id);
CREATE INDEX IF NOT EXISTS idx_fiscal_years_status ON fiscal_years (status);

CREATE TABLE IF NOT EXISTS bank_reconciliation_sessions (

    id                INTEGER NOT NULL,
    company_id        BIGINT         NOT NULL,
    bank_account_id   BIGINT         NOT NULL,
    statement_date    DATE           NOT NULL,
    opening_balance   DECIMAL(19, 2) NOT NULL,
    transaction_total DECIMAL(19, 2) NOT NULL,
    ending_balance    DECIMAL(19, 2) NOT NULL,
    register_balance  DECIMAL(19, 2) NULL,
    report_lines_captured BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at      DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bank_reconciliation_sessions_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_bank_reconciliation_sessions_account FOREIGN KEY (bank_account_id) REFERENCES chart_of_accounts (id)
);
CREATE INDEX IF NOT EXISTS idx_bank_reconciliation_sessions_company_account ON bank_reconciliation_sessions (company_id, bank_account_id);
CREATE INDEX IF NOT EXISTS idx_bank_reconciliation_sessions_statement_date ON bank_reconciliation_sessions (statement_date);

CREATE TABLE IF NOT EXISTS bank_reconciliation_report_lines (

    id               INTEGER NOT NULL,
    session_id       BIGINT         NOT NULL,
    journal_entry_id BIGINT         NOT NULL,
    transaction_type VARCHAR(32)    NOT NULL,
    transaction_date DATE           NOT NULL,
    document_number  VARCHAR(100)   NULL,
    name             VARCHAR(255)   NULL,
    description      VARCHAR(500)   NULL,
    amount           DECIMAL(19, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bank_reconciliation_report_journal_entry UNIQUE (journal_entry_id),
    CONSTRAINT fk_bank_reconciliation_report_session FOREIGN KEY (session_id)
        REFERENCES bank_reconciliation_sessions (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_bank_reconciliation_report_session ON bank_reconciliation_report_lines (session_id);

CREATE TABLE IF NOT EXISTS bank_categorization_rules (
    id                 INTEGER NOT NULL,
    company_id         BIGINT       NOT NULL,
    name               VARCHAR(120) NOT NULL,
    match_text         VARCHAR(255) NOT NULL,
    priority           INTEGER      NOT NULL,
    target_account_id  BIGINT       NOT NULL,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bank_categorization_rules_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_bank_categorization_rules_account FOREIGN KEY (target_account_id) REFERENCES chart_of_accounts (id)
);
CREATE INDEX IF NOT EXISTS idx_bank_categorization_rules_company_priority
    ON bank_categorization_rules (company_id, priority, id);

CREATE TABLE IF NOT EXISTS bank_transactions (

    id                        INTEGER NOT NULL,
    company_id                BIGINT         NOT NULL,
    bank_account_id           BIGINT         NOT NULL,
    transaction_date          DATE           NOT NULL,
    description               VARCHAR(1000)  NOT NULL,
    amount                    DECIMAL(19, 2) NOT NULL,
    reference                 VARCHAR(500)   NULL,
    fit_id                    VARCHAR(255)   NULL,
    source_hash               VARCHAR(64)    NOT NULL,
    source_row_hash           VARCHAR(64)    NOT NULL,
    suggested_category_id     BIGINT         NULL,
    categorization_rule_id    BIGINT         NULL,
    category_reviewed         BOOLEAN        NOT NULL DEFAULT FALSE,
    reconciled                BOOLEAN        NOT NULL DEFAULT FALSE,
    reconciliation_session_id BIGINT         NULL,
    created_at                DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bank_transactions_company_account_row UNIQUE (company_id, bank_account_id, source_row_hash),
    CONSTRAINT fk_bank_transactions_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_bank_transactions_account FOREIGN KEY (bank_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_bank_transactions_suggested_category FOREIGN KEY (suggested_category_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_bank_transactions_categorization_rule FOREIGN KEY (categorization_rule_id)
        REFERENCES bank_categorization_rules (id),
    CONSTRAINT fk_bank_transactions_session FOREIGN KEY (reconciliation_session_id)
        REFERENCES bank_reconciliation_sessions (id)
);
CREATE INDEX IF NOT EXISTS idx_bank_transactions_company_account_status ON bank_transactions (company_id, bank_account_id, reconciled);
CREATE INDEX IF NOT EXISTS idx_bank_transactions_source_hash ON bank_transactions (company_id, bank_account_id, source_hash);
CREATE INDEX IF NOT EXISTS idx_bank_transactions_session ON bank_transactions (reconciliation_session_id);
CREATE INDEX IF NOT EXISTS idx_bank_transactions_suggested_category ON bank_transactions (company_id, suggested_category_id);

CREATE TABLE IF NOT EXISTS written_cheques (

    id INTEGER NOT NULL,
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
    CONSTRAINT uk_written_cheques_company_number UNIQUE (company_id, cheque_number),
    CONSTRAINT fk_written_cheques_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_written_cheques_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id),
    CONSTRAINT fk_written_cheques_bank FOREIGN KEY (bank_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_written_cheques_reissue FOREIGN KEY (reissue_of_id) REFERENCES written_cheques (id)
);
CREATE INDEX IF NOT EXISTS idx_written_cheques_company ON written_cheques (company_id);

CREATE TABLE IF NOT EXISTS cheque_expenses (

    id INTEGER NOT NULL,
    cheque_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    customer_job_id BIGINT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    memo VARCHAR(500) NULL,
    tax VARCHAR(40) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_cheque_expenses_cheque FOREIGN KEY (cheque_id) REFERENCES written_cheques (id) ON DELETE CASCADE,
    CONSTRAINT fk_cheque_expenses_account FOREIGN KEY (account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_cheque_expenses_customer FOREIGN KEY (customer_job_id) REFERENCES customers (id)
);
CREATE INDEX IF NOT EXISTS idx_cheque_expenses_cheque ON cheque_expenses (cheque_id);

CREATE TABLE IF NOT EXISTS credit_card_charges (

    id INTEGER NOT NULL,
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
    CONSTRAINT fk_credit_card_charges_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_credit_card_charges_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id),
    CONSTRAINT fk_credit_card_charges_expense FOREIGN KEY (expense_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_credit_card_charges_card FOREIGN KEY (card_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_credit_card_charges_journal FOREIGN KEY (journal_id) REFERENCES general_journals (id),
    CONSTRAINT fk_credit_card_charges_reissue FOREIGN KEY (reissue_of_id) REFERENCES credit_card_charges (id)
);
CREATE INDEX IF NOT EXISTS idx_credit_card_charges_company ON credit_card_charges (company_id);

CREATE TABLE IF NOT EXISTS transfers (

    id INTEGER NOT NULL,
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
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_transfers_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_transfers_from_account FOREIGN KEY (from_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_transfers_to_account FOREIGN KEY (to_account_id) REFERENCES chart_of_accounts (id),
    CONSTRAINT fk_transfers_journal FOREIGN KEY (journal_id) REFERENCES general_journals (id)
);
CREATE INDEX IF NOT EXISTS idx_transfers_company ON transfers (company_id);

CREATE TABLE IF NOT EXISTS document_attachments (

    id             INTEGER NOT NULL,
    company_id     BIGINT        NOT NULL,
    invoice_id     BIGINT        NULL,
    bill_id        BIGINT        NULL,
    filename       VARCHAR(255)  NOT NULL,
    content_type   VARCHAR(100)  NOT NULL,
    content_length BIGINT        NOT NULL,
    created_at     DATETIME   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_document_attachment_owner CHECK (
        (invoice_id IS NOT NULL AND bill_id IS NULL)
        OR (invoice_id IS NULL AND bill_id IS NOT NULL)
    ),
    CONSTRAINT fk_document_attachments_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_document_attachments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_attachments_bill FOREIGN KEY (bill_id) REFERENCES bills (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_document_attachments_company ON document_attachments (company_id);
CREATE INDEX IF NOT EXISTS idx_document_attachments_invoice ON document_attachments (invoice_id);
CREATE INDEX IF NOT EXISTS idx_document_attachments_bill ON document_attachments (bill_id);

CREATE TABLE IF NOT EXISTS document_attachment_contents (

    attachment_id BIGINT    NOT NULL,
    content       BLOB  NOT NULL,
    PRIMARY KEY (attachment_id),
    CONSTRAINT fk_document_attachment_contents_attachment FOREIGN KEY (attachment_id)
        REFERENCES document_attachments (id) ON DELETE CASCADE
);
