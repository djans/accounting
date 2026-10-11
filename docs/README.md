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
existing data. After setup, an administrator can use **Administration /
Database engine migration** to copy all application data between MySQL and
SQLite in either direction. The destination schema is recreated, so all
destination data is replaced; back up both databases and stop other application
users and instances from using the installation during the copy. MySQL
migration needs a destination JDBC URL, username, password, and privileges to
create the database and create/drop its tables; SQLite migration needs a
writable file path on the application host. The destination connection is
saved to the runtime configuration, and the application must be restarted
immediately after a successful migration.
When MySQL is the current source, the migration page shows its active JDBC URL
and username plus its password in a masked, read-only field for administrators.

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
- Company-scoped user access and role management for administrators (see [`USER_ACCESS.md`](USER_ACCESS.md)).
- Double-entry journals, a general ledger, fiscal years, and filtered accounting
  reports with before- and after-closing trial balances and PDF downloads.
- Tax agencies, codes, filing periods, calculations, and reconciliation.
- Bank reconciliation, transfers, cheques, and credit-card charges.
- CSV and OFX/QFX bank statement import with company-specific categorization suggestions (see [`BANK_RECONCILIATION.md`](BANK_RECONCILIATION.md)).
- Invoice and bill attachments, PDF generation, optional invoice email, and database backup/restore.
- MySQL and SQLite persistence.
- Categorized operational, error, and internal-process logs, with an administrator monitoring page and protected Spring Actuator health and metrics endpoints.

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
- **Database schema needs an update:** sign in as an administrator and use `/database/schema`; see the schema-versioning guide.
