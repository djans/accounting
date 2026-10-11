# Architecture

This is a modular monolith: one Spring Boot application serves the browser UI and application APIs, and coordinates the accounting workflows in-process. It uses Java 25, Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data JPA, and Hibernate.

## Application capabilities

The business capabilities are organized as Spring services in one deployable application, not as separate services or Maven modules:

- **Identity and companies**: users, roles, company profiles, company memberships, company selection, and company-scoped access.
- **Sales and receivables**: customers, invoices and line items, payments, invoice delivery, customer account statements, and billing reports.
- **Purchases and payables**: vendors, bills and line items, bill payments, and payable reports.
- **Accounting**: chart of accounts, general journals and entries, general ledger balances, fiscal years and closing, transfers, and financial reports.
- **Canadian tax**: tax agencies, items, groups and codes, GST/HST/QST calculations, filing periods, return snapshots, and tax reports.
- **Cash operations**: bank transactions, statement reconciliation, written cheques, and credit-card charges.
- **Documents and administration**: invoice PDFs and email, protected invoice/bill attachments, backup and restore, schema management, and initial-data migration.

Invoice, bill, and payment workflows can create and post corresponding journal entries through the posting services. Journal reversals are used when supported transactions are changed or cancelled. Reconciliation imports identify duplicate source rows and associate statement activity with company bank accounts and posted journal entries. Invoice-list downloads bundle generated invoice PDFs and attached PDFs; customer statements summarize invoice charges and payment credits for a selected period.

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

`Main` checks for the local database configuration at
`${user.home}/.accounting/database.properties` by default; the location can be
overridden with `APP_DATABASE_CONFIG_PATH`. If the file is absent, `Main`
starts the database setup web application without initializing the accounting
application or requiring a database connection.

Shared Spring configuration lives in `src/main/resources/application.yml`.
Spring loads `application-<profile>.yml` on top of that shared configuration.
The default profile is `local`; `development`, `staging`, and `production` can
be selected with `SPRING_PROFILES_ACTIVE` or
`--spring.profiles.active=<profile>`. Database credentials are kept in the
external properties file, not in the shared or profile YAML files.

The setup page supports:

- **MySQL**: a JDBC URL, username, and password. The setup verifies the connection and requests database creation when needed; the MySQL account must have the required database privileges.
- **SQLite**: a filesystem path. The setup creates the file if needed and refuses to configure a database that already contains user-defined database objects.

The settings are written outside the repository and imported by Spring Boot on
the next application start. `APP_DATABASE_CONFIG_PATH` can point to a mounted
configuration file in container deployments. The setup server binds to
`127.0.0.1` by default. Remote setup requires both `APP_SETUP_BIND_ADDRESS` and
a strong `APP_SETUP_TOKEN`; Docker deployments must persist `/root/.accounting`
so the configuration and SQLite database survive container replacement.
Oracle and Db2 are not implemented.

## Persistence and schema lifecycle

Most entity persistence uses Spring Data JPA repository interfaces. Application services depend on those interfaces rather than a database vendor's implementation. Custom JDBC operations use repository contracts where appropriate, with shared SQL kept in `repository/jdbc/` and dialect-specific behavior supplied by engine adapters.

Hibernate does not create or update the application schema (`spring.jpa.hibernate.ddl-auto=none`). Spring SQL initialization uses the schema selected in the saved configuration:

- `src/main/resources/schema.sql` is the MySQL DDL.
- `src/main/resources/schema-sqlite.sql` is the SQLite DDL.

These files initialize new databases and are used by a full schema reset; their SQL is not interchangeable. Schema-version migrations for populated databases are registered in `DatabaseSchemaVersionService` and stored under `src/main/resources/db/migration/`. The administrator reviews and applies pending migrations through `/database/schema`. The Administration **Reset** page offers an independent current-company activity reset and a destructive full-database restart that recreates the active engine's schema. After a full restart, the administrator must restart the application and sign in again before optionally importing a supported initial-data package from the same Reset workflow. That import is not a backup/restore and only applies to an empty accounting dataset.

An administrator can copy the complete database between MySQL and SQLite in either direction through `/admin/database/migrate`. The source is the active connection; the destination requires its engine-specific settings. The destination schema is recreated and its data is replaced, then the runtime database configuration is saved for the next start. This operation requires a backup and a maintenance window; the application must be restarted immediately after migration.
When MySQL is the active source, the administrator migration page displays its current JDBC URL, username, and masked password to help retain the connection details for a future return to MySQL.

When a change affects entities, columns, relationships, constraints, or indexes, update **both** engine schema files, add a versioned migration for existing databases, and update the related tests and any dialect-specific adapter. Follow [docs/SCHEMA_VERSIONING.md](docs/SCHEMA_VERSIONING.md) for the release procedure. Adding another database engine also requires its driver, dialect, schema, configuration path, and validation coverage.

JDBC adapters execute their SQL directly; configuring a Hibernate dialect does not translate it. When adding a database engine:

- Extend `DatabaseSetupService` with URL validation and the engine's driver, Hibernate dialect, schema, and connection options.
- Review `JdbcCompanyMembershipRepository`, especially `grant()`: its `INSERT ... SELECT` has no `FROM` clause and may need an engine-specific implementation.
- Keep common SQL in `JdbcCompanyActivityRepository` portable and provide an engine adapter for `resetBankReconciliation()` as needed. `MysqlCompanyActivityRepository` and `SqliteCompanyActivityRepository` are the existing examples; validate activity counting and reset behavior for the new engine.
- `DatabaseSetupSecurityConfiguration` is database-engine agnostic, but verify the setup security chain remains active in the database setup mode.
- Add integration coverage for setup and the membership and activity repositories.

## Security boundaries

Spring Security protects the application routes and role-restricted operations. Company-scoped services resolve the active company from the authenticated user and its memberships rather than trusting a company identifier supplied by a request. Database credentials are stored outside source control; remote first-run configuration is token-protected.

## Operations and monitoring

`LoggingCategory` marks application events as `USER_ACTIVITY`, `ERROR`, or
`INTERNAL_PROCESS`. Logback sends every event to the console and unified
`accounting.log`, and routes categorized events to corresponding
`accounting-<CATEGORY>.log` files. Logs are written under `logs/` by default;
`APP_LOG_PATH` changes the directory. Daily compressed archives are kept under
`archive/`.
Rejected database migrations are logged in the `ERROR` category with the
administrator, destination engine, and rejection reason key, without passwords
or submitted connection details.
Technical migration failures include a stack trace with configured passwords
and URL password parameters redacted.

Spring Actuator exposes only health, info, and metrics over HTTP. Both those
endpoints and `/admin/monitoring` require the administrator role. The
monitoring page displays health component statuses without health details and
a bounded list of metric measurements without tags. Its metric table can be
filtered by metric name and scales byte values to readable binary units.
