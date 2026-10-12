# Database schema versions

The installed schema version starts at `1.0.0`. The current application version is declared by the highest entry in `DatabaseSchemaVersionService.MIGRATIONS`; version `1.1.0` introduces the schema-version ledger, `1.2.0` adds the invoice `tax_regime` column, `1.3.0` moves authorization roles to company memberships and adds unique user ShortIDs, `1.4.0` adds OFX/QFX import identity and company-scoped bank categorization, `1.5.0` adds company logo storage and cancellation audit fields for invoices and bills, `1.6.0` adds company-scoped invoice template storage, `1.7.0` adds storage for final invoice PDF snapshots, `1.8.0` adds credit-note headers and line items, `1.9.0` adds customer-credit refunds, and `1.10.0` adds customer-credit allocations to other invoices.

## Keeping database schemas in sync

Whenever a change affects the stored structure (entities, columns, relationships, constraints, or indexes), update both engine schemas:

- `src/main/resources/schema.sql` is the MySQL schema.
- `src/main/resources/schema-sqlite.sql` is the SQLite schema.

Keep their effective table and column structures aligned with each other and with the JPA entities. The files use different SQL dialects; do not copy MySQL DDL into the SQLite file. A schema change is incomplete until both files and the relevant schema/migration tests have been updated.

These schema files initialize new databases and are used by the destructive schema reset. They do not upgrade populated installations. For those, also provide and register a forward-only migration as described below. Keep the migration idempotent where possible, account for each supported SQL dialect, and update any database-specific repository or migration implementation affected by the change. When adding another database engine, add its own schema and validation path before declaring it supported.

## Keeping initial-data migration documentation in sync

Whenever a database or entity change affects data handled by
`InitialDataMigrationService`, review its package headers, parsing and
validation rules, relationship checks, entity mapping, and migration tests.
Keep `docs/INITIAL_DATA_MIGRATION_V1.md` and the documentation shown at
`/database/migration/documentation` accurate in both English and French.
Review and update the downloadable examples in
`src/main/resources/migration/examples/v1/`, then validate the generated ZIP
with the importer in a test. Document fields intentionally excluded from the
import. Never silently change the meaning or required headers of package
version 1; use a new package version for incompatible changes and retain
support for previously released versions.

## Releasing a schema change

1. Update both engine schema files so fresh databases and a full schema reset get the latest structure.
2. Create a forward-only SQL migration in `src/main/resources/db/migration/`, named `V<major>_<minor>_<patch>__<description>.sql`, for already-populated databases.
3. Register its `SchemaMigration` in `DatabaseSchemaVersionService.MIGRATIONS` with a version greater than the preceding entry. Increment the minor or patch component for compatible changes; increment the major component for a breaking schema change.
4. Set `requiresBackup` when the migration can discard, truncate, or reinterpret existing data. The update page will require the SuperAdmin applying the global migration to confirm that a backup was downloaded.
5. Keep each migration safe to retry if possible. The runner applies every registered migration after the installed version, in semantic-version order, and records each completed version. It stops at the first failure and does not mark that migration complete.
6. Do not use the schema files or legacy startup migration runners as a substitute for versioned migrations on existing databases. New releases must wait for the administrator's approval on the schema-version page.

After sign-in, accounts still using the initial credentials are first offered the credential-update page. Choosing to continue without changing them remembers that choice in the current browser, so the prompt is not shown again there. A stale or unsupported schema is shown at `/database/schema`. Company administrators can review known versions; only SuperAdmins can download the whole-database backup or apply the full pending migration sequence. Unknown database versions and missing migration scripts are blocked rather than guessed.

Version `1.3.0` copies each existing account's legacy role to its company
memberships (`VIEWER` becomes `AUDITOR`) and adds the account ShortID column.
The migration resumes safely if only some of its columns or index were added
before an earlier attempt failed. The application assigns existing ShortIDs
after the schema update. The legacy
`app_users.role` column may remain in upgraded databases, but authorization
uses only `user_company_memberships.role`.

Version `1.4.0` adds bank FITID and suggested-category/review columns plus the
company-scoped `bank_categorization_rules` table. It uses retry-safe,
database-specific DDL for both MySQL and SQLite. Bank statement transactions
are not part of the initial-data migration package.

Version `1.5.0` adds nullable company logo data and content type, plus
cancellation reason and timestamp fields for invoices and supplier bills. It
uses retry-safe, database-specific DDL for MySQL and SQLite.
The nullable columns are also ensured by a pre-authentication compatibility
step so Hibernate can load users and companies on installations that have not
yet opened the schema-version page. This does not advance the version ledger;
the SuperAdmin still applies the pending versioned migration there.

Version `1.6.0` adds company-owned DOCX invoice templates and a company index.
Template data is stored as a database binary large object and is not included
in the initial-data migration package.

Version `1.7.0` adds the `invoice_final_pdfs` table for immutable PDF snapshots
of invoices issued as final. The snapshots are part of database backups, but
are intentionally excluded from the initial-data migration package.

Version `1.8.0` adds company-scoped credit notes and their line items. Issued
credit notes retain a link to the source invoice and are included in database
backups, but are intentionally excluded from initial-data migration package v1.

Version `1.9.0` adds auditable cash refunds for customer credit created by
issued credit notes. Version `1.10.0` adds auditable allocations of that
available credit to another invoice for the same customer, including reversal
date and reason. Credit-note refunds, allocations, and their related records
are included in database backups, but remain intentionally excluded from
initial-data migration package v1.

The **Restart from scratch** action on the Administration Reset page is a destructive SuperAdmin operation. It offers a backup download, drops the database tables, reinstalls the schema for the active database engine, and applies every registered migration. The reset removes user accounts. The `local` profile defaults the first administrator to `root@example.com` / `password`; `development`, `staging`, and `production` require `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD` when bootstrapping an empty database after reset. Configure `APP_SUPER_ADMIN_EMAILS` after reset to restore global SuperAdmin access. After restarting the application and signing in again, a SuperAdmin can optionally use the initial-data import linked from Reset; see [the package contract](INITIAL_DATA_MIGRATION_V1.md).
