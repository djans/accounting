# Initial Data Migration Package v1

This is a **new-install, one-time** import format. It is not a database backup
or restore format. The existing JSON backup/restore flow is separate and is not
used by this feature.

Only an administrator can open `/database/migration`. The page first validates
the upload and shows a row-count preview. It writes nothing during validation.
The administrator must then type `IMPORT` to submit the short-lived,
single-user validation token. A preview expires after 15 minutes.

## Safety policy

The importer never deletes, updates, or merges records. It will reject both
validation and confirmation if the migration-owned accounting domain already
has an account, customer, vendor, invoice, bill, journal, journal line, or
general-ledger row. Login/company and tax configuration records are not part of
this initial-data package.

The confirmed write is one serializable transaction. A failed write rolls back
every JPA entity created by the package.

The journal CSVs are the authoritative accounting history for this format.
Importing an invoice or bill does not synthesize a new posting journal, so
source packages must include the corresponding journal history when it is
needed. Payment transaction rows are not part of v1; `paid_amount` is imported
as the document's historical summary and detailed payment evidence remains
outside this format.

The development reference-data seeders create accounts and vendors, so a new
deployment receiving this package must start with
`APP_SEED_REFERENCE_DATA_ENABLED=false` (or
`app.seed.reference-data.enabled=false`). The administrator/bootstrap company
records are allowed. Keep reference seeding disabled after the import unless
those extra records are explicitly desired.

## ZIP layout

Upload a UTF-8 ZIP file no larger than 20 MiB. All ten entries below are
required, including CSV files with no data rows. No extra, duplicate, or
directory entries are allowed.

```text
manifest.json
accounts.csv
customers.csv
vendors.csv
invoices.csv
invoice_lines.csv
bills.csv
bill_lines.csv
journal_entries.csv
journal_lines.csv
```

`manifest.json` must contain exactly:

```json
{
  "format": "cogitosum-accounting-migration",
  "version": 1
}
```

CSV is UTF-8 with a comma delimiter. RFC-style quoted fields and doubled quote
escapes are supported. Header names and their order are canonical: each header
below must match exactly. Dates are ISO `YYYY-MM-DD`; `posted_date` is an
optional local ISO date-time such as `2026-01-31T16:45:00`. Monetary values are
non-negative decimal amounts with at most two places. Boolean values are
lowercase `true` or `false`.

To prevent spreadsheet formula injection, text values may not begin with
`=`, `+`, `-`, or `@`. Validation messages name a file and row but never repeat
uploaded cell values.

## Canonical headers

```text
# accounts.csv
source_id,account_number,account_name,account_type,description,is_active

# customers.csv
source_id,name,email,business_name,address,city,province,postal_code,country,business_number,gst_number

# vendors.csv
source_id,name,email,business_name,address,city,province,postal_code,country,business_number,gst_number,qst_number

# invoices.csv
source_id,invoice_number,customer_source_id,invoice_date,due_date,status,subtotal,gst_amount,hst_amount,qst_amount,total_amount,paid_amount,tax_regime,notes

# invoice_lines.csv
source_id,invoice_source_id,line_number,description,quantity,unit_price,total

# bills.csv
source_id,bill_number,vendor_source_id,bill_date,due_date,status,subtotal,gst_amount,hst_amount,qst_amount,total_amount,paid_amount,notes

# bill_lines.csv
source_id,bill_source_id,line_number,expense_account_source_id,description,quantity,unit_price,total

# journal_entries.csv
source_id,journal_number,journal_date,narrative,reference,status,posted_date,posted_by

# journal_lines.csv
source_id,journal_source_id,line_number,account_source_id,debit,credit,cleared,description
```

## IDs, references, and validation

`source_id` is a package-local identifier and is never written into an
application primary-key column. It must be unique in its file and use only
letters, digits, `.`, `_`, `:`, and `-`. The importer maps source identifiers
to newly generated JPA IDs in this dependency order:

1. accounts, customers, and vendors;
2. invoices and bills with their lines;
3. journals with their lines.

The following reference columns must point to a source ID in the named file:

| Column | Target file |
| --- | --- |
| `customer_source_id` | `customers.csv` |
| `vendor_source_id` | `vendors.csv` |
| `invoice_source_id` | `invoices.csv` |
| `bill_source_id` | `bills.csv` |
| `expense_account_source_id` (optional) | `accounts.csv` |
| `journal_source_id` | `journal_entries.csv` |
| `account_source_id` | `accounts.csv` |

Account numbers, customer emails, vendor emails, invoice numbers, bill
numbers, journal numbers, and all source IDs are checked for duplicates before
any write. An invoice and bill each require one or more lines; their line
numbers must be unique within the parent. Each line `total` must be
`quantity × unit_price`, rounded to two places; document subtotals must equal
their imported line totals; document totals must equal subtotal plus all three
tax columns; and `paid_amount` cannot exceed `total_amount`.

`account_type` accepts the `AccountType` enum values:
`ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`, `CONTRA_ASSET`, and
`CONTRA_LIABILITY`.

`invoices.csv` accepts `DRAFT`, `SENT`, `VIEWED`, `PARTIALLY_PAID`, `PAID`,
`OVERDUE`, `CANCELLED`, and `REFUNDED`. `bills.csv` accepts `DRAFT`,
`RECEIVED`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, and `CANCELLED`.

Journal status accepts `DRAFT`, `POSTED`, `REVERSED`, and `CANCELLED`. Every
journal needs at least two lines with unique line numbers, exactly one non-zero
debit or credit on each line, and balanced total debits and credits. Imported
`POSTED` and `REVERSED` journal entries contribute their lines to the
newly-created general-ledger balances; draft and cancelled entries do not.
