# Architecture

This is a modular monolith: one Spring Boot application serves the browser UI and application APIs, and coordinates the accounting workflows in-process. It uses Java 25, Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data JPA, and Hibernate.

## Application capabilities

The business capabilities are organized as Spring services in one deployable application, not as separate services or Maven modules:

- **Identity and companies**: users, roles, company profiles, company memberships, company selection, and company-scoped access.
- **Sales and receivables**: customers, invoices and line items, payments, invoice delivery, and billing reports.
- **Purchases and payables**: vendors, bills and line items, bill payments, and payable reports.
- **Accounting**: chart of accounts, general journals and entries, general ledger balances, fiscal years and closing, transfers, and financial reports.
- **Canadian tax**: tax agencies, items, groups and codes, GST/HST/QST calculations, filing periods, return snapshots, and tax reports.
- **Cash operations**: bank transactions, statement reconciliation, written cheques, and credit-card charges.
- **Documents and administration**: invoice PDFs and email, protected invoice/bill attachments, backup and restore, schema management, and initial-data migration.

Invoice, bill, and payment workflows can create and post corresponding journal entries through the posting services. Journal reversals are used when supported transactions are changed or cancelled. Reconciliation imports identify duplicate source rows and associate statement activity with company bank accounts and posted journal entries.

## Request and business flow

```text
Browser (Thymeleaf) or API client
                  |
      Spring Security filter chain
                  |
       web/ controllers and DTOs
                  |
             services
       /        |         \
 identity   billing &    accounting,
 /company   operations   tax & reports
       \        |         /
    JPA repositories / JDBC contracts
                  |
          MySQL or SQLite
```

For an authenticated company request, `CurrentCompanyContext` resolves the active company from the enabled user and, when a company is selected in the session, verifies that the user belongs to it. Services use that context for company-owned records rather than trusting a company identifier from request data. Controllers adapt HTTP forms or JSON to service calls and return a Thymeleaf view or API response; DTOs keep API payloads separate from JPA entities.

The normal write path is:

1. A controller validates the HTTP-level input and invokes a service.
2. The service applies business rules and company checks within a transaction.
3. Posting services coordinate journal creation, posting, or reversal when the business operation requires it.
4. Repository contracts persist or load records; JPA is used for entity repositories and JDBC adapters are used for custom SQL operations.
5. The controller returns the page, redirect, or API result. Shared exception handling maps application errors to user-facing or API responses.

Authentication is shared by both interfaces: form login serves the web UI, and API authentication endpoints serve clients. Role rules are declared in `SecurityConfig`; company membership and `CompanyOwned` checks provide the additional tenant boundary.

## Runtime layers

```text
Browser / API client
        |
  web / controller
        |
     service
        |
   repository contracts
    /             \
 Spring Data JPA  JDBC adapters
        \             /
     MySQL or SQLite
```

- `entity/` contains the JPA entities and company-owned data model.
- `controller/` exposes application API endpoints; `web/` serves the Thymeleaf pages.
- `service/` holds use cases, business rules, and transaction boundaries.
- `dto/` defines API/request/response shapes independently of entity persistence.
- `exception/` contains shared application error handling and response types.
- `repository/` contains Spring Data JPA repositories and application-facing repository contracts.
- `repository/jdbc/` contains JDBC implementations shared across engines where the SQL is portable.
- `repository/mysql/` and `repository/sqlite/` contain engine-specific repository behavior.
- `config/` contains security, bootstrap, reference-data setup, and compatibility migrations.
- `setup/` validates database connection details and persists the local database configuration.
- `migration/v1/` handles versioned initial-data import packages. Database schema migrations are separate and live in `src/main/resources/db/migration/`.

## Database selection and startup

`Main` checks for the local database configuration at `${user.home}/.accounting/database.properties`. If it is absent, the application first checks whether the existing MySQL configuration can connect to an already-created database. If so, it saves those settings locally to preserve an existing installation. Otherwise, it starts the database setup web application without initializing the accounting application or requiring a database connection.

The setup page supports:

- **MySQL**: a JDBC URL, username, and password. The setup verifies the connection and requests database creation when needed; the MySQL account must have the required database privileges.
- **SQLite**: a filesystem path. The setup creates the file if needed and refuses to configure a database that already contains user-defined database objects.

The settings are written outside the repository and imported by Spring Boot on the next application start. The setup server binds to `127.0.0.1` by default. Remote setup requires both `APP_SETUP_BIND_ADDRESS` and a strong `APP_SETUP_TOKEN`; Docker deployments must persist `/root/.accounting` so the configuration and SQLite database survive container replacement. Oracle and Db2 are not implemented.

## Persistence and schema lifecycle

Most entity persistence uses Spring Data JPA repository interfaces. Application services depend on those interfaces rather than a database vendor's implementation. Custom JDBC operations use repository contracts where appropriate, with shared SQL kept in `repository/jdbc/` and dialect-specific behavior supplied by engine adapters.

Hibernate does not create or update the application schema (`spring.jpa.hibernate.ddl-auto=none`). Spring SQL initialization uses the schema selected in the saved configuration:

- `src/main/resources/schema.sql` is the MySQL DDL.
- `src/main/resources/schema-sqlite.sql` is the SQLite DDL.

These files initialize new databases and are used by a full schema reset; their SQL is not interchangeable. Schema-version migrations for populated databases are registered in `DatabaseSchemaVersionService` and stored under `src/main/resources/db/migration/`. The administrator reviews and applies pending migrations through `/database/schema`. A full reset is destructive and recreates the schema for the active engine.

When a change affects entities, columns, relationships, constraints, or indexes, update **both** engine schema files, add a versioned migration for existing databases, and update the related tests and any dialect-specific adapter. Follow [docs/SCHEMA_VERSIONING.md](docs/SCHEMA_VERSIONING.md) for the release procedure. Adding another database engine also requires its driver, dialect, schema, configuration path, and validation coverage.

## Security boundaries

Spring Security protects the application routes and role-restricted operations. Company-scoped services resolve the active company from the authenticated user and its memberships rather than trusting a company identifier supplied by a request. Database credentials are stored outside source control; remote first-run configuration is token-protected.
