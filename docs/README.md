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
repository at `${user.home}/.accounting/database.properties`. Oracle and DB2
are not supported. Switching database engines does not migrate existing data.

The first-run setup server binds to `127.0.0.1` by default. To make it
accessible remotely, configure both `APP_SETUP_BIND_ADDRESS` and a strong
`APP_SETUP_TOKEN`. Persist `/root/.accounting` when running the Docker image so
the database configuration and SQLite file survive container replacement.

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
application's JSON backup and restore.

### Docker

```bash
docker build -t accounting:1.0 .
docker-compose up
```

For direct `docker run` setup, configure the setup bind address and token and
mount a persistent volume at `/root/.accounting`.

## Application features

- Company-scoped customer, invoice, payment, vendor, bill, and bill-payment workflows.
- Double-entry journals, a general ledger, fiscal years, and accounting reports.
- Tax agencies, codes, filing periods, calculations, and reconciliation.
- Bank reconciliation, transfers, cheques, and credit-card charges.
- Invoice and bill attachments, PDF generation, optional invoice email, and database backup/restore.
- MySQL and SQLite persistence.

## Documentation

- [`API_DOCUMENTATION.md`](API_DOCUMENTATION.md) — current REST endpoint inventory.
- [`GENERAL_JOURNAL_GUIDE.md`](GENERAL_JOURNAL_GUIDE.md) — journal workflows and examples.
- [`ARCHITECTURE.md`](../ARCHITECTURE.md) — application layers, database setup, and schema lifecycle.
- [`SCHEMA_VERSIONING.md`](SCHEMA_VERSIONING.md) — schema update and migration procedure.
- [`INITIAL_DATA_MIGRATION_V1.md`](INITIAL_DATA_MIGRATION_V1.md) — initial-data import package contract.

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
