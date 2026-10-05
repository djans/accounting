# Database schema versions

The installed schema version starts at `1.0.0`. The current application version is declared by the highest entry in `DatabaseSchemaVersionService.MIGRATIONS`; version `1.1.0` introduces the schema-version ledger. Databases without a ledger are treated as the `1.0.0` baseline because earlier releases maintained their schema at startup.

## Releasing a schema change

1. Create a forward-only SQL script in `src/main/resources/db/migration/`, named `V<major>_<minor>_<patch>__<description>.sql`.
2. Register its `SchemaMigration` in `DatabaseSchemaVersionService.MIGRATIONS` with a version greater than the preceding entry. Increment the minor or patch component for compatible changes; increment the major component for a breaking schema change.
3. Set `requiresBackup` when the migration can discard, truncate, or reinterpret existing data. The update page will require the administrator to confirm that a backup was downloaded.
4. Keep each script safe to retry if possible. The runner applies every registered migration after the installed version, in semantic-version order, and records each completed version. It stops at the first failure and does not mark that migration complete.
5. Do not add new release-specific DDL to `schema.sql` or the legacy startup migration runners. `schema.sql` and those runners represent the existing `1.0.0` baseline; new releases must wait for the administrator's approval on the schema-version page.

After sign-in, accounts still using the initial credentials are first offered the credential-update page. Once continued, a stale or unsupported schema is shown at `/database/schema`. Administrators can download a backup, review all known versions, and apply the full pending migration sequence. Unknown database versions and missing migration scripts are blocked rather than guessed.

The complete reset at `/admin/database/recreate` is destructive. It offers a backup download, drops the database tables, reinstalls the baseline schema, and applies every registered migration. The reset removes user accounts; after restarting, the first administrator is bootstrapped from `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD`, which default to `root@example.com` and `password`.
