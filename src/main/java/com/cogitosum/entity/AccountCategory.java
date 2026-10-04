package com.cogitosum.entity;

public enum AccountCategory {
    BANK("Bank"),
    CREDIT_CARD("Credit Card"),
    ACCOUNTS_RECEIVABLE("Accounts Receivable"),
    OTHER_CURRENT_ASSET("Other Current Asset"),
    FIXED_ASSET("Fixed Asset"),
    ACCOUNTS_PAYABLE("Accounts Payable"),
    OTHER_CURRENT_LIABILITY("Other Current Liability"),
    LONG_TERM_LIABILITY("Long Term Liability"),
    EQUITY("Equity"),
    INCOME("Income"),
    OTHER_INCOME("Other Income"),
    COST_OF_GOODS_SOLD("Cost of Goods Sold"),
    EXPENSE("Expense"),
    OTHER_EXPENSE("Other Expense");

    private final String label;

    AccountCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
