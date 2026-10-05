# Copilot project rules

## Java and Spring versions

- Generate and update code for Java 25, Spring Boot 4.1.1, and Spring Framework 7.0.9.
- Prefer APIs and configuration compatible with these versions; do not introduce older-version patterns or deprecated APIs when a current alternative is available.

## Localization

- Preserve both `src/main/resources/messages_fr.properties` and `src/main/resources/messages_en.properties`.
- Preserve every `src/main/resources/i18n/*_fr.properties` and `*_en.properties` bundle.
- Never rename message keys with a locale suffix such as `_fr`, `_fr_CA`, `_en`, or `_en_CA`.
- Locale-specific files contain translations only; templates and Java code must always reference the stable key (for example `#{stat.customers}`).
- When adding a translatable label, add the same stable key to both the French and English bundles.
- Do not delete or replace existing translation entries while changing unrelated features.
- When a translation key is no longer referenced by Java code or templates, remove it from both language bundles; do not retain unused translation properties.
- Keep locale fallback behavior language-based: regional locales such as `fr_CA` must resolve to `fr`, and `en_CA` to `en`.

Before completing a change, verify that both language bundles still contain the keys used by modified templates.

## Initial-data migration packages

- When changing `schema.sql`, an entity, or persistence rules for data handled by `InitialDataMigrationService`, review the package contract and its validation/import path in the same change.
- Keep the package file list, CSV headers, parsing and relationship validation, entity mapping, and migration tests consistent with the supported schema.
- If a change affects the package format incompatibly, introduce a new format version and preserve support for existing versions; do not silently change the meaning of version 1.
- Update migration-package documentation and both English and French translations when their behavior or instructions change.
