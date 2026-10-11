package com.cogitosum.repository;

public interface CompanyActivityRepository {

    ActivityCounts findActivityCounts(Long companyId);

    BankReconciliationCounts findBankReconciliationCounts(Long companyId);

    void resetActivity(Long companyId);

    void resetBankReconciliation(Long companyId);

    record ActivityCounts(
            long invoices,
            long customerPayments,
            long bills,
            long vendorPayments,
            long cheques,
            long creditCardCharges,
            long transfers,
            long journals,
            long taxFilingPeriods,
            long fiscalYears,
            long importedBankTransactions,
            long reconciliationSessions) {

        public long total() {
            return invoices + customerPayments + bills + vendorPayments + cheques
                    + creditCardCharges + transfers + journals + taxFilingPeriods + fiscalYears
                    + importedBankTransactions + reconciliationSessions;
        }
    }

    record BankReconciliationCounts(long importedBankTransactions, long reconciliationSessions) {
    }
}
