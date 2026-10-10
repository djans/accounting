# REST API Reference

This document lists the REST routes currently exposed by the application's
controllers. It is an endpoint index, not a replacement for request DTO
validation or business rules in the services.

The application uses Java 25, Spring Boot 4.1.1, Spring Framework 7.0.9,
Spring Data JPA, and Hibernate. MySQL and SQLite are the supported database
engines; configure them through the first-run setup flow. H2 and PostgreSQL
configuration examples from older revisions no longer apply.

API requests use the application's security and company-scoping rules. The
required authentication and roles depend on the endpoint. Database setup,
browser pages, and schema administration are web flows rather than REST API
routes. See [`ARCHITECTURE.md`](../ARCHITECTURE.md) for the application and
security model.

## Billing and sales

### Customers — `/api/customers`

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/customers` | Create a customer |
| GET | `/api/customers` | List customers |
| GET | `/api/customers/{id}` | Get a customer |
| GET | `/api/customers/email/{email}` | Find by email |
| GET | `/api/customers/gst/{gstNumber}` | Find by GST number |
| PUT | `/api/customers/{id}` | Update a customer |
| DELETE | `/api/customers/{id}` | Delete a customer |

### Invoices — `/api/invoices`

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/invoices` | Create an invoice |
| GET | `/api/invoices` | List invoices |
| GET | `/api/invoices/{id}` | Get an invoice |
| GET | `/api/invoices/number/{invoiceNumber}` | Find by invoice number |
| GET | `/api/invoices/customer/{customerId}` | List a customer's invoices |
| GET | `/api/invoices/status/{status}` | Filter by status |
| GET | `/api/invoices/date-range` | Filter by date range |
| GET | `/api/invoices/overdue` | List overdue invoices |
| PUT | `/api/invoices/{id}` | Update an invoice |
| PUT | `/api/invoices/{id}/send` | Mark as sent |
| PUT | `/api/invoices/{id}/view` | Mark as viewed |
| PUT | `/api/invoices/{id}/cancel` | Cancel an invoice |
| DELETE | `/api/invoices/{id}` | Delete an invoice |

### Payments — `/api/payments`

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/payments` | Record a payment |
| GET | `/api/payments` | List payments |
| GET | `/api/payments/{id}` | Get a payment |
| GET | `/api/payments/transaction/{transactionId}` | Find by transaction ID |
| GET | `/api/payments/invoice/{invoiceId}` | List payments for an invoice |
| GET | `/api/payments/status/{status}` | Filter by status |
| GET | `/api/payments/date-range` | Filter by date range |
| PUT | `/api/payments/{id}/complete` | Complete a payment |
| PUT | `/api/payments/{id}/refund` | Refund a payment |
| DELETE | `/api/payments/{id}` | Delete a payment |

### Vendors — `/api/vendors`

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/vendors` | Create a vendor |
| GET | `/api/vendors` | List vendors |
| GET | `/api/vendors/{id}` | Get a vendor |
| GET | `/api/vendors/email/{email}` | Find by email |
| PUT | `/api/vendors/{id}` | Update a vendor |
| DELETE | `/api/vendors/{id}` | Delete a vendor |

### Bills — `/api/bills`

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/bills` | Create a bill |
| GET | `/api/bills` | List bills |
| GET | `/api/bills/{id}` | Get a bill |
| GET | `/api/bills/vendor/{vendorId}` | List a vendor's bills |
| GET | `/api/bills/status/{status}` | Filter by status |
| GET | `/api/bills/date-range` | Filter by date range |
| GET | `/api/bills/overdue` | List overdue bills |
| PUT | `/api/bills/{id}` | Update a bill |
| PUT | `/api/bills/{id}/receive` | Mark a bill as received |
| PUT | `/api/bills/{id}/cancel` | Cancel a bill |
| DELETE | `/api/bills/{id}` | Delete a bill |

### Bill payments — `/api/bill-payments`

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/bill-payments` | Record a bill payment |
| GET | `/api/bill-payments` | List bill payments |
| GET | `/api/bill-payments/{id}` | Get a bill payment |
| GET | `/api/bill-payments/bill/{billId}` | List payments for a bill |
| PUT | `/api/bill-payments/{id}/complete` | Complete a bill payment |
| PUT | `/api/bill-payments/{id}/refund` | Refund a bill payment |
| DELETE | `/api/bill-payments/{id}` | Delete a bill payment |

### Billing reports — `/api/reports`

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/reports/revenue` | Revenue report |
| GET | `/api/reports/aging` | Receivables aging |
| GET | `/api/reports/invoice-status-summary` | Invoice status totals |
| GET | `/api/reports/tax-summary` | Billing tax summary |

## Accounting

### Chart of accounts — `/api/chart-of-accounts`

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/chart-of-accounts` | Create an account |
| GET | `/api/chart-of-accounts` | List accounts |
| GET | `/api/chart-of-accounts/active` | List active accounts |
| GET | `/api/chart-of-accounts/{id}` | Get an account |
| GET | `/api/chart-of-accounts/number/{accountNumber}` | Find by account number |
| GET | `/api/chart-of-accounts/type/{type}` | Filter by account type |
| PUT | `/api/chart-of-accounts/{id}` | Update an account |
| DELETE | `/api/chart-of-accounts/{id}` | Delete an account |

### Journals — `/api/journals`

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/journals` | Create a journal |
| GET | `/api/journals` | List journals |
| GET | `/api/journals/{id}` | Get a journal |
| GET | `/api/journals/number/{journalNumber}` | Find by journal number |
| GET | `/api/journals/status/{status}` | Filter by status |
| GET | `/api/journals/date-range` | Filter by date range |
| GET | `/api/journals/posted/date-range` | List posted journals by date |
| PUT | `/api/journals/{id}` | Update a journal |
| PUT | `/api/journals/{id}/post` | Post a journal |
| PUT | `/api/journals/{id}/reverse` | Reverse a journal |
| DELETE | `/api/journals/{id}` | Delete a journal |

### General ledger — `/api/general-ledger`

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/general-ledger` | List ledger balances |
| GET | `/api/general-ledger/account/{accountId}` | Get a ledger balance by account |

### Accounting reports — `/api/accounting-reports`

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/accounting-reports/trial-balance` | Trial balance |
| GET | `/api/accounting-reports/balance-sheet` | Balance sheet |
| GET | `/api/accounting-reports/income-statement` | Income statement |
| GET | `/api/accounting-reports/account-detail/{accountId}` | Account detail |
| GET | `/api/accounting-reports/account-transactions/{accountId}` | Account transactions |
| GET | `/api/accounting-reports/account-type-balances` | Balances by account type |

### Fiscal years — `/api/fiscal-years`

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/fiscal-years` | Create a fiscal year |
| GET | `/api/fiscal-years` | List fiscal years |
| GET | `/api/fiscal-years/{id}` | Get a fiscal year |
| PUT | `/api/fiscal-years/{id}/close` | Close a fiscal year |
| PUT | `/api/fiscal-years/{id}/reopen` | Reopen a fiscal year |
| DELETE | `/api/fiscal-years/{id}` | Delete a fiscal year |

## Tax

### Tax agencies — `/api/tax-agencies`

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/tax-agencies` | List agencies |
| GET | `/api/tax-agencies/{id}` | Get an agency |
| POST | `/api/tax-agencies` | Create an agency |
| PUT | `/api/tax-agencies/{id}` | Update an agency |
| DELETE | `/api/tax-agencies/{id}` | Delete an agency |
| GET | `/api/tax-agencies/{id}/report` | Get an agency report |

### Tax codes — `/api/tax-codes`

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/tax-codes` | List tax codes |
| GET | `/api/tax-codes/{id}` | Get a tax code |
| POST | `/api/tax-codes` | Create a tax code |
| PUT | `/api/tax-codes/{id}` | Update a tax code |
| DELETE | `/api/tax-codes/{id}` | Delete a tax code |

### Tax filings — `/api/tax-filings`

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/tax-filings` | List filings |
| GET | `/api/tax-filings/{id}` | Get a filing |
| POST | `/api/tax-filings` | Create a filing |
| POST | `/api/tax-filings/{id}/calculate` | Calculate a filing |
| POST | `/api/tax-filings/{id}/file` | Mark a filing as filed |
| POST | `/api/tax-filings/{id}/pay` | Record filing payment |
| POST | `/api/tax-filings/{id}/cancel` | Cancel a filing |

### Tax impact — `/api/tax/impact`

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/tax/impact/agency/{id}` | Get agency tax impact |
| GET | `/api/tax/impact/item/{id}` | Get item tax impact |
| GET | `/api/tax/impact/group/{id}` | Get group tax impact |
| GET | `/api/tax/impact/code/{id}` | Get code tax impact |

### Tax reports — `/api/tax`

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/tax/reconciliation` | Tax reconciliation report |
| GET | `/api/tax/agencies` | Agency report data |
| GET | `/api/tax/codes` | Tax-code report data |
| GET | `/api/tax/periods` | Filing-period report data |
| GET | `/api/tax/periods/{id}` | Filing-period detail |

## Request and response models

The request and response shapes are defined by the relevant controller and any
DTO or entity it uses, with write rules enforced by controller/service
validation. Consult those classes when integrating a route; do not infer
writable fields from database columns alone.

Date-range routes accept `startDate` and `endDate` query parameters where
implemented. See the individual controller method for exact parameter
requirements and validation.

For browser workflows, database setup, attachments, backup/restore, and schema
administration, see [`README.md`](README.md) and
[`ARCHITECTURE.md`](../ARCHITECTURE.md).
