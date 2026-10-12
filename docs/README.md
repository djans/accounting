# Canadian Accounting System

## Quick start

### Requirements

- Java 25
- Maven
- Docker (optional)

Build and run locally:

```bash
mvn clean install
mvn spring-boot:run
```

The application is available at `http://localhost:8080`.

### First database setup

When no local database configuration exists, the first launch opens the setup
page at `http://localhost:8080/setup`. Select MySQL or SQLite, enter the
connection details or SQLite file path, test the connection, and save. Restart
the application to initialize the schema.

MySQL setup requires credentials that can create the selected database. SQLite
creates a new database file. The saved settings are stored outside the
repository at `${user.home}/.accounting/database.properties` by default.
Override the file location with `APP_DATABASE_CONFIG_PATH`. Oracle and DB2 are
not supported. Changing the engine during first-run setup does not migrate
existing data. After setup, a SuperAdmin can use **Administration / Database
engine migration** to copy all application data between MySQL and
SQLite in either direction. The destination schema is recreated, so all
destination data is replaced; back up both databases and stop other application
users and instances from using the installation during the copy. MySQL
migration needs a destination JDBC URL, username, password, and privileges to
create the database and create/drop its tables; SQLite migration needs a
writable file path on the application host. The destination connection is
saved to the runtime configuration, and the application must be restarted
immediately after a successful migration.
When MySQL is the current source, the migration page shows its active JDBC URL
and username plus its password in a masked, read-only field for SuperAdmins.

Keep this file readable only by the account running the application. Setup
creates it with owner-only POSIX permissions where supported; on Windows it
inherits the permissions of its parent directory.

Shared application settings are in `src/main/resources/application.yml`.
Database connection details are imported from the external properties file;
they are not kept in the application YAML.

The first-run setup server binds to `127.0.0.1` by default. To make it
accessible remotely, configure both `APP_SETUP_BIND_ADDRESS` and a strong
`APP_SETUP_TOKEN`. Persist `/root/.accounting` when running the Docker image so
the database configuration and SQLite file survive container replacement.

## Spring profiles

The common configuration is in `application.yml`; profile-specific overrides
are in `application-local.yml`, `application-development.yml`,
`application-staging.yml`, and `application-production.yml`. The default
profile is `local`, so `mvn spring-boot:run` continues to work without extra
arguments. Select another profile with `SPRING_PROFILES_ACTIVE` set to
`development`, `staging`, or `production`, or
`--spring.profiles.active=development`.
Development, staging, and production profiles do not use the default
bootstrap credentials. Set `APP_BOOTSTRAP_ADMIN_EMAIL` and
`APP_BOOTSTRAP_ADMIN_PASSWORD` before first initialization or after a full
database reset; existing installations with an administrator do not need them.
Set `APP_SUPER_ADMIN_EMAILS` to one or more comma-separated account emails to
designate global SuperAdmins. Company administrators are authorized only for
companies where they hold an administrator membership. Only SuperAdmins can
export or restore the whole-database backup, apply global schema migrations,
restart the database, or access database-wide diagnostics and migration tools.
The local profile defaults this setting to `root@example.com`; all other
profiles require it to be configured explicitly. See
[Company user access and roles](USER_ACCESS.md) for the per-company scopes.
For a new or reset database, include the bootstrap administrator's email in
`APP_SUPER_ADMIN_EMAILS` if that account must perform global operations.

### Optional invoice email

Invoice email is disabled by default. To enable it, provide runtime
configuration through environment variables:

```text
APP_INVOICE_MAIL_ENABLED=true
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=...
SMTP_PASSWORD=...
INVOICE_MAIL_FROM=accounts@example.com
```

Do not commit credentials to source control.

### Invoice and bill attachments

Invoice and bill pages accept PDF, JPEG, and PNG attachments up to 10 MiB.
Uploads are company-scoped and stored in the database; they are included in the
application's JSON backup and restore. From the invoice list, select invoices
to download their generated PDFs and attached PDFs together; one PDF downloads
directly and multiple PDFs are packaged in a ZIP. The customer statement page
shows activity for a selected customer and date range and offers a PDF download.

### Invoice PDF template

Invoice PDFs are rendered from a DOCX template, populated with invoice data,
then converted by LibreOffice Writer. Before downloading a single invoice or a
selection, choose a template. Company administrators manage company-specific
templates under **Administration / Invoice templates**, where they can upload,
download, and delete their company's DOCX files. Each upload has a name and
language tag; the matching language is suggested when downloading an invoice.
The built-in template remains available, and email attachments continue to use
it. The static wording in a custom DOCX must be translated in that document.
When creating an invoice, **Final invoice?** issues and posts the invoice,
locks it against edits, and saves a PDF snapshot using the selected template.
The same finalization option is available on a draft invoice below the customer
details. A draft can also be deleted; its invoice number is released for reuse.
Later downloads of a finalized invoice use its saved PDF instead of rendering
the invoice again. Emailing a draft also issues it and stores a final PDF
snapshot after the email is successfully sent.

Use `docs/invoice-template-example_en.docx` or
`docs/invoice-template-example_fr.docx` as references for supported
placeholders. The repeat markers `{{#lineItems}}` and `{{/lineItems}}` must stay
in the same table row as the line-item fields. Place `{{company.logo}}` where the logo should
appear; it is optional and scaled to fit within 2 by 0.7 inches. Company
profiles accept PNG or JPEG logos up to
1 MiB. Unknown placeholders prevent PDF generation. Uploaded templates must
be valid DOCX files no larger than 5 MiB. The DOCX must be rendered for each
invoice because values and line-item counts vary; converting an empty template
to PDF once would not populate those fields.

Use `{{company.address_1}}` for the company's street address and
`{{company.address_2}}` for postal code, city, province, and country. Use
`{{customer.address_1}}` and `{{customer.address_2}}` for the corresponding
customer fields. The previous `{{company.address}}` and
`{{customer.address}}` placeholders remain supported for existing templates.

LibreOffice is installed in the Docker image; local installations need
LibreOffice installed. On Windows, the application checks the standard Program
Files locations for `soffice.exe`; on Linux it looks for `libreoffice` or
`soffice` on `PATH`. Set `APP_INVOICE_PDF_LIBREOFFICE_COMMAND` to the
executable path if LibreOffice is installed elsewhere.

### Invoice and supplier-bill corrections

Invoices and supplier bills can be edited or deleted only while they are
drafts. Once an invoice is issued or a supplier bill is received, preserve the
original record. Correct an issued invoice with a credit note rather than
editing or cancelling it; supplier bills can be cancelled with a reason after
reversing any payments. The cancellation reason and time remain on the
cancelled record. This application workflow is not a substitute for
professional accounting or legal advice about required documents or tax
reporting.

### Credit notes

From an issued invoice, choose **Create credit note** to open a draft linked to
that invoice. Adjust its original invoice lines, enter a reason and date, then
issue it from the credit-note detail page. A draft has no accounting or tax
effect. Issuing rechecks the remaining uncredited invoice value while locking
the invoice, calculates GST/HST/QST proportionally from the tax amounts saved
on the original invoice, and posts the reversing journal entry. Issued notes
reduce the customer's balance, appear as credits on customer statements, and
reduce sales and tax totals in the filing period of their issue date. A fully
settled invoice that has been credited is marked **Credited**. A credit note
may be issued even when an invoice has been paid. If it creates a customer
credit beyond the amount still due on its source invoice, the credit-note
detail page can either refund that surplus or apply it to another open invoice
for the same customer. Applications may be partial, are recorded with their
date and target invoice, and can be reversed with an audit reason. They reduce the target invoice's balance without
creating another payment, credit note, tax adjustment, or general-ledger
entry. The credit note's existing revenue and tax reversal remains the sole
accounting entry for the credit; allocation only settles the customer's
receivable subledger. Refunding and applying credit both reduce the same
available amount, so the surplus cannot be used twice. Each cash refund is
recorded separately against an active bank account and posts a debit to
accounts receivable and a credit to the bank account; it does not reverse the
original customer payment. Use **Print / Save as PDF** on the credit-note
detail page to print or save a copy from the browser.
Supplier payments affect the bill's paid total once when recorded. Marking a
payment completed does not add it again; refunds and voids reverse the total
while retaining the payment record.

### Docker

```bash
docker build -t accounting:1.0 .
docker-compose up
```

For direct `docker run` setup, configure the setup bind address and token and
mount a persistent volume at `/root/.accounting`. Docker Compose defaults to
the `production` profile; set `SPRING_PROFILES_ACTIVE` to override it. Supply
bootstrap administrator credentials through environment variables when
initializing an empty database.

## Application features

- Company-scoped customer, invoice, payment, vendor, bill, and bill-payment workflows.
- Invoice credit notes with linked source documents, reversing ledger entries,
  auditable customer refunds, and applications to other invoices for the same
  customer.
- Company-scoped user access and role management for administrators (see [`USER_ACCESS.md`](USER_ACCESS.md)).
- Double-entry journals, a general ledger, fiscal years, and filtered accounting
  reports with before- and after-closing trial balances and PDF downloads.
- The company dashboard summarizes fiscal-year-to-date revenue and net profit,
  plus current general-ledger cash, tax payable, accounts receivable, and
  accounts payable balances.
- Tax agencies, codes, filing periods, calculations, and reconciliation.
- Bank reconciliation, transfers, cheques, and credit-card charges.
- CSV and OFX/QFX bank statement import with company-specific categorization suggestions (see [`BANK_RECONCILIATION.md`](BANK_RECONCILIATION.md)).
- Invoice and bill attachments, PDF generation, optional invoice email, and database backup/restore.
- MySQL and SQLite persistence.
- Categorized operational, error, and internal-process logs, with a SuperAdmin monitoring page and protected Spring Actuator health and metrics endpoints.

The Administration **Reset** page combines a current-company activity reset
with a separate **Restart from scratch** option that deletes and recreates the
entire active database, including all companies and user accounts. The initial
data package import is linked from this page as an optional third step after a
full restart: restart the application, sign in with the recreated administrator
account, then validate and import into the empty dataset. A company activity
reset is independent. Initial-data packages are not backups or restores and
contain only the supported accounting records documented in
[`INITIAL_DATA_MIGRATION_V1.md`](INITIAL_DATA_MIGRATION_V1.md). The import
page and its documentation provide a downloadable, validated v1 example ZIP.

Administrators can grant or revoke company membership and set company-specific
roles from **Administration / Company users**. See
[`USER_ACCESS.md`](USER_ACCESS.md) before granting existing users access across
companies.

## Operations, logs, and monitoring

Application logs are written to the console and to `logs/accounting.log`.
Category files are `logs/accounting-USER_ACTIVITY.log`,
`logs/accounting-ERROR.log`, and `logs/accounting-INTERNAL_PROCESS.log`.
Daily compressed archives are stored under `logs/archive/`; set `APP_LOG_PATH`
to change the log directory. The unified log contains all categories.
Rejected database migrations are recorded in the `ERROR` category with the
administrator, target engine, and safe reason key; submitted passwords and
connection form values are not included. Technical migration failures include
a sanitized stack trace to help diagnose the failure; configured passwords and
password query parameters are redacted.

Administrators can review component health and Micrometer metrics under
**Administration / Health and metrics**. The read-only Actuator endpoints
`/actuator/health`, `/actuator/info`, and `/actuator/metrics` are restricted to
administrators; health details are not exposed. Metrics shown in the page are
limited to 40 names and omit metric tags. Filter the table by metric name; byte
measurements are displayed in B, KiB, MiB, GiB, TiB, or PiB as appropriate.

## Documentation

- [`API_DOCUMENTATION.md`](API_DOCUMENTATION.md) — current REST endpoint inventory.
- [`GENERAL_JOURNAL_GUIDE.md`](GENERAL_JOURNAL_GUIDE.md) — journal workflows and examples.
- [`BANK_RECONCILIATION.md`](BANK_RECONCILIATION.md) — statement imports, duplicate detection, and categorization rules.
- [`ARCHITECTURE.md`](../ARCHITECTURE.md) — application layers, database setup, and schema lifecycle.
- [`SCHEMA_VERSIONING.md`](SCHEMA_VERSIONING.md) — schema update and migration procedure.
- [`INITIAL_DATA_MIGRATION_V1.md`](INITIAL_DATA_MIGRATION_V1.md) — initial-data import package contract.
- [`USER_ACCESS.md`](USER_ACCESS.md) — company membership management, current role limits, and future role design.

## Schema maintenance

When a database structure changes, update both `src/main/resources/schema.sql`
(MySQL) and `src/main/resources/schema-sqlite.sql` (SQLite), add and register a
versioned migration for existing databases, and update the related tests.
Follow [`SCHEMA_VERSIONING.md`](SCHEMA_VERSIONING.md) for the full procedure.

## Sample API calls

API access is subject to application authentication, authorization, and
company-scoping rules. See [`API_DOCUMENTATION.md`](API_DOCUMENTATION.md) for
the full route list.

Create a customer:

```bash
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Smith",
    "email": "john@example.com",
    "businessName": "Smith Consulting Inc.",
    "address": "123 Main Street",
    "city": "Toronto",
    "province": "ON",
    "postalCode": "M5H 2N2",
    "country": "Canada"
  }'
```

Create an invoice using the returned customer ID:

```bash
curl -X POST http://localhost:8080/api/invoices \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "lineItems": [
      {
        "description": "Consulting services",
        "quantity": 2,
        "unitPrice": 150.00
      }
    ]
  }'
```

## Troubleshooting

- **Port 8080 is already in use:** configure another `server.port`.
- **Database setup cannot connect:** verify the MySQL URL and privileges, or that the SQLite path is writable.
- **Database schema needs an update:** sign in as a company administrator to review `/database/schema`; a SuperAdmin must apply global updates. See the schema-versioning guide.
