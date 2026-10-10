# Database schema versions

The installed schema version starts at `1.0.0`. The current application version is declared by the highest entry in `DatabaseSchemaVersionService.MIGRATIONS`; version `1.1.0` introduces the schema-version ledger. Databases without a ledger are treated as the `1.0.0` baseline because earlier releases maintained their schema at startup.

## Keeping database schemas in sync

Whenever a change affects the stored structure (entities, columns, relationships, constraints, or indexes), update both engine schemas:

- `src/main/resources/schema.sql` is the MySQL schema.
- `src/main/resources/schema-sqlite.sql` is the SQLite schema.

Keep their effective table and column structures aligned with each other and with the JPA entities. The files use different SQL dialects; do not copy MySQL DDL into the SQLite file. A schema change is incomplete until both files and the relevant schema/migration tests have been updated.

These schema files initialize new databases and are used by the destructive schema reset. They do not upgrade populated installations. For those, also provide and register a forward-only migration as described below. Keep the migration idempotent where possible, account for each supported SQL dialect, and update any database-specific repository or migration implementation affected by the change. When adding another database engine, add its own schema and validation path before declaring it supported.

## Releasing a schema change

1. Update both engine schema files so fresh databases and a full schema reset get the latest structure.
2. Create a forward-only SQL migration in `src/main/resources/db/migration/`, named `V<major>_<minor>_<patch>__<description>.sql`, for already-populated databases.
3. Register its `SchemaMigration` in `DatabaseSchemaVersionService.MIGRATIONS` with a version greater than the preceding entry. Increment the minor or patch component for compatible changes; increment the major component for a breaking schema change.
4. Set `requiresBackup` when the migration can discard, truncate, or reinterpret existing data. The update page will require the administrator to confirm that a backup was downloaded.
5. Keep each migration safe to retry if possible. The runner applies every registered migration after the installed version, in semantic-version order, and records each completed version. It stops at the first failure and does not mark that migration complete.
6. Do not use the schema files or legacy startup migration runners as a substitute for versioned migrations on existing databases. New releases must wait for the administrator's approval on the schema-version page.

After sign-in, accounts still using the initial credentials are first offered the credential-update page. Once continued, a stale or unsupported schema is shown at `/database/schema`. Administrators can download a backup, review all known versions, and apply the full pending migration sequence. Unknown database versions and missing migration scripts are blocked rather than guessed.

The complete reset at `/admin/database/recreate` is destructive. It offers a backup download, drops the database tables, reinstalls the schema for the active database engine, and applies every registered migration. The reset removes user accounts; after restarting, the first administrator is bootstrapped from `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD`, which default to `root@example.com` and `password`.
