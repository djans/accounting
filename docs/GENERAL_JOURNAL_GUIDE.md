# General Journal User Guide

This guide covers journal workflows and examples. For the full current REST
endpoint inventory, see [`API_DOCUMENTATION.md`](API_DOCUMENTATION.md).

## Overview

The General Journal system adds a complete double-entry accounting layer to your billing system. It enables you to:

- Create and post journal entries with automatic debit/credit balancing
- Maintain a General Ledger with running account balances
- Generate financial statements (Trial Balance, Balance Sheet, Income Statement)
- Track all accounting transactions with full audit trail
- Support journal reversals for correcting past entries

## Key Concepts

### Chart of Accounts (COA)
A master list of all accounts your company uses for recording transactions.

**Account Types:**
- **ASSET** (1000-1999) - Cash, Accounts Receivable, Equipment
- **LIABILITY** (2000-2999) - Accounts Payable, GST Payable, Notes Payable
- **EQUITY** (3000-3999) - Owner's Capital, Retained Earnings
- **REVENUE** (4000-4999) - Sales, Service Revenue
- **EXPENSE** (5000-5999) - Rent, Salaries, Utilities
- **CONTRA_ASSET** - Allowance for Doubtful Accounts
- **CONTRA_LIABILITY** - Discounts on Bonds Payable

### General Journal
The main journal for recording all accounting transactions.

**Journal Statuses:**
- **DRAFT** - Journal is being prepared and can be edited or deleted.
- **POSTED** - Journal affects the general ledger. Editing posted journals is
  controlled by the company's posted-journal editing setting.
- **REVERSED** - The posted journal has been reversed; the system also creates
  and posts an offsetting journal.
- **CANCELLED** - A retained status value; the REST API does not expose a
  journal-cancellation action.

### Journal Entries
Individual debit/credit lines within a journal.

**Requirements:**
- Each journal must balance (Total Debits = Total Credits)
- Each entry must reference an account from the Chart of Accounts

### General Ledger
Maintains running balances for each account automatically updated when journals are posted.

## API Endpoints

### Chart of Accounts Management

#### Create Account
```
POST /api/chart-of-accounts
Content-Type: application/json

{
  "accountNumber": "1010",
  "accountName": "Cash",
  "accountType": "ASSET",
  "description": "Company operating bank account",
  "isActive": true
}
```

#### Get All Accounts
```
GET /api/chart-of-accounts
```

#### Get Active Accounts
```
GET /api/chart-of-accounts/active
```

#### Get Account by ID
```
GET /api/chart-of-accounts/{id}
```

#### Get Account by Number
```
GET /api/chart-of-accounts/number/{accountNumber}
```

#### Get Accounts by Type
```
GET /api/chart-of-accounts/type/{type}

Type values: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE, CONTRA_ASSET, CONTRA_LIABILITY
```

#### Update Account
```
PUT /api/chart-of-accounts/{id}
Content-Type: application/json
```

#### Delete Account
```
DELETE /api/chart-of-accounts/{id}
```

### General Journal Management

#### Create Journal (Draft)
```
POST /api/journals
Content-Type: application/json

{
  "journalDate": "2024-05-24",
  "narrative": "Record sales transaction for Invoice INV-001",
  "reference": "INV-001",
  "entries": [
    {
      "account": {"id": 1},
      "debit": 1000.00,
      "credit": 0.00,
      "description": "Accounts Receivable - Customer A",
      "lineNumber": 1
    },
    {
      "account": {"id": 4},
      "debit": 0.00,
      "credit": 1000.00,
      "description": "Sales Revenue",
      "lineNumber": 2
    }
  ]
}
```

#### Get All Journals
```
GET /api/journals
```

#### Get Journal by ID
```
GET /api/journals/{id}
```

#### Get Journal by Number
```
GET /api/journals/number/{journalNumber}
```

#### Get Journals by Status
```
GET /api/journals/status/{status}

Status values: DRAFT, POSTED, REVERSED, CANCELLED
```

#### Get Journals by Date Range
```
GET /api/journals/date-range?startDate=2024-05-01&endDate=2024-05-31
```

#### Get Posted Journals by Date Range
```
GET /api/journals/posted/date-range?startDate=2024-05-01&endDate=2024-05-31
```

#### Update Journal (Draft Only)
```
PUT /api/journals/{id}
Content-Type: application/json
```

#### Post Journal
Finalizes the journal and updates the General Ledger.

```
PUT /api/journals/{id}/post?postedBy=John%20Smith
```

Response:
```json
{
  "id": 1,
  "journalNumber": "JL-ABC12345",
  "status": "POSTED",
  "postedDate": "2024-05-24T10:30:00",
  "postedBy": "John Smith"
}
```

#### Reverse Journal
Creates an offsetting journal entry to reverse a posted journal.

```
PUT /api/journals/{id}/reverse?reason=Incorrect%20account%20coding
```

#### Delete Journal (Draft Only)
```
DELETE /api/journals/{id}
```

### General Ledger

#### Get All Ledger Accounts
```
GET /api/general-ledger
```

Returns running balances for all accounts.

#### Get Ledger by Account ID
```
GET /api/general-ledger/account/{accountId}
```

Response:
```json
{
  "id": 1,
  "account": {
    "id": 1,
    "accountNumber": "1010",
    "accountName": "Cash"
  },
  "debitBalance": 5000.00,
  "creditBalance": 1000.00,
  "balance": 4000.00,
  "lastUpdated": "2024-05-24T10:30:00"
}
```

### Financial Reports

#### Trial Balance
```
GET /api/accounting-reports/trial-balance
```

Response:
```json
{
  "accounts": {
    "1010": {
      "accountNumber": "1010",
      "accountName": "Cash",
      "debitBalance": 5000.00,
      "creditBalance": 1000.00,
      "balance": 4000.00
    },
    "1200": {
      "accountNumber": "1200",
      "accountName": "Accounts Receivable",
      "debitBalance": 2000.00,
      "creditBalance": 0.00,
      "balance": 2000.00
    }
  },
  "totalDebits": 7000.00,
  "totalCredits": 7000.00,
  "balanced": true
}
```

#### Balance Sheet
```
GET /api/accounting-reports/balance-sheet
```

Response:
```json
{
  "totalAssets": 10000.00,
  "totalLiabilities": 3000.00,
  "totalEquity": 7000.00,
  "balanced": true,
  "totalLiabilitiesAndEquity": 10000.00
}
```

#### Income Statement
```
GET /api/accounting-reports/income-statement
```

Response:
```json
{
  "totalRevenue": 15000.00,
  "totalExpenses": 5000.00,
  "netIncome": 10000.00
}
```

#### Account Type Balances
```
GET /api/accounting-reports/account-type-balances
```

Response:
```json
{
  "ASSET": 10000.00,
  "LIABILITY": 3000.00,
  "EQUITY": 7000.00,
  "REVENUE": 15000.00,
  "EXPENSE": 5000.00
}
```

#### Account Detail
```
GET /api/accounting-reports/account-detail/{accountId}
```

#### Account Transactions
```
GET /api/accounting-reports/account-transactions/{accountId}
```

## Integration with Billing System

### Journal Entries from Billing Workflows

Supported invoice, bill, and payment posting workflows create and post journal
entries. Typical invoice entries include:

#### When Invoice is Created
```
Debit: Accounts Receivable
Credit: Sales Revenue
Credit: GST/HST/QST Payable, when applicable
```

#### When Payment is Received
```
Entry: Debit Cash, Credit Accounts Receivable
```

#### Example: Setup Auto-Journal from Invoice

1. **Create Chart of Accounts:**
   - 1200 Accounts Receivable (ASSET)
   - 2300 GST Payable (LIABILITY)
   - 4010 Sales Revenue (REVENUE)

2. **When Invoice Created:**
   ```
   POST /api/journals
   {
     "narrative": "Invoice created",
     "reference": "INV-001",
     "entries": [
       {"account": {"id": 3}, "debit": 1656.50, "credit": 0},
       {"account": {"id": 4}, "debit": 0, "credit": 1570.00},
       {"account": {"id": 2}, "debit": 0, "credit": 86.50}
     ]
   }
   ```

3. **Then Post It:**
   ```
   PUT /api/journals/{id}/post?postedBy=System
   ```

## Typical Chart of Accounts Setup (Canadian Company)

### ASSETS (1000-1999)
- 1010 Cash
- 1020 Petty Cash
- 1100 Accounts Receivable
- 1150 Allowance for Doubtful Accounts (Contra-Asset)
- 1200 Short-term Investments
- 1500 Equipment
- 1550 Accumulated Depreciation - Equipment (Contra-Asset)

### LIABILITIES (2000-2999)
- 2100 Accounts Payable
- 2200 GST Payable
- 2210 HST Payable
- 2300 PST Payable
- 2400 Accrued Expenses
- 2500 Short-term Loans

### EQUITY (3000-3999)
- 3100 Owner's Capital
- 3200 Retained Earnings
- 3300 Current Period Earnings

### REVENUE (4000-4999)
- 4010 Sales Revenue
- 4100 Service Revenue
- 4200 Interest Income

### EXPENSES (5000-5999)
- 5010 Salaries and Wages
- 5020 Rent Expense
- 5030 Utilities Expense
- 5040 Office Supplies
- 5050 Depreciation Expense
- 5060 Professional Fees
- 5070 Marketing Expense

## Workflow Example

### Step 1: Setup Chart of Accounts
```bash
# Create accounts
POST /api/chart-of-accounts (Cash, AR, Revenue, etc.)
```

### Step 2: Create Journal Entry (Draft)
```bash
# Create new journal in draft status
POST /api/journals
```

### Step 3: Review and Edit (if needed)
```bash
# Update journal while in DRAFT
PUT /api/journals/{id}
```

### Step 4: Post Journal
```bash
# Finalize journal - updates GL
PUT /api/journals/{id}/post?postedBy=accountant_name
```

### Step 5: Review Financial Reports
```bash
# Check trial balance
GET /api/accounting-reports/trial-balance

# Get balance sheet
GET /api/accounting-reports/balance-sheet

# Get income statement
GET /api/accounting-reports/income-statement
```

## Validation Rules

1. **Journal Balance:** Total Debits must equal Total Credits
2. **Account Validation:** All accounts must exist in Chart of Accounts
3. **Status Workflow:** DRAFT journals can be posted; a POSTED journal can be
   reversed, which posts an offsetting journal and marks the original REVERSED.
4. **Edit Restrictions:** Only DRAFT journals can be deleted. Editing a POSTED
   journal is allowed only when enabled for the company; fiscal-year locks
   still apply.

## Error Handling

Common errors you may encounter:

| Error | Cause | Solution |
|-------|-------|----------|
| Journal is not balanced | Debits ≠ Credits | Adjust entries so debits equal credits |
| Only DRAFT journals can be posted | Trying to post non-draft | Check journal status |
| Only POSTED journals can be reversed | Trying to reverse draft/already reversed | Post the journal first |
| Account does not exist | Invalid account ID | Create account first in Chart of Accounts |

## Best Practices

1. **Use Reference Numbers:** Always link to source documents (Invoice, PO, etc.)
2. **Clear Narratives:** Enter descriptive text for audit trail
3. **Balanced Approach:** Ensure every debit has corresponding credit
4. **Regular Reviews:** Check trial balance regularly
5. **Reversal not Delete:** Use reversal for posted entries instead of deletion

## Audit Trail

All journals maintain:
- Creation timestamp
- Posted date and posted by user
- Status history
- Reference to source documents
- Complete entry details

This ensures full compliance and traceability for accounting and tax purposes.
