# Bank statement import and categorization

From **Reconcile / bank account**, upload a UTF-8 CSV, OFX, or QFX statement.
CSV continues to use the existing `Date,Description,Amount[,Reference]` import
format. OFX/QFX supports OFX 1.x SGML and OFX 2.x XML bank statement
transactions; dates, signed amounts, FITIDs, payee names, and memos are read.
Malformed, unsupported, empty, or partially invalid statements are rejected
before any transaction is saved. XML parsing disables DTDs and external entity
resolution.

Imports are company- and bank-account-scoped. The source-file SHA-256 prevents
reimporting an identical file, while a stable row hash prevents reimporting
transactions across differently formatted files. OFX/QFX FITIDs are used as
stable row identity when present; without a FITID the transaction's date,
description, amount, and reference are hashed. Duplicate rows within a file
reject the entire import.

Use **Manage categorization rules** to match case-insensitive text contained
in a transaction description or reference. The matching active rule with the
smallest priority number wins, with rule ID breaking ties. Target accounts
must belong to the current company and cannot be bank accounts. Suggestions
are informational only and remain unreviewed until explicitly confirmed in
the account's imported-transactions list; imports do not post journal entries.

Imported statement transactions and ledger journal entries are displayed
separately. The existing reconciliation workflow continues to select posted
ledger journal entries; importing a statement alone does not reconcile or
create accounting entries.
