package com.cogitosum.service;

import com.cogitosum.repository.CompanyActivityRepository;
import com.cogitosum.repository.CompanyActivityRepository.ActivityCounts;
import com.cogitosum.repository.CompanyActivityRepository.BankReconciliationCounts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyActivityResetService {

    private final CompanyActivityRepository activityRepository;
    private final CurrentCompanyContext companyContext;

    public CompanyActivityResetService(CompanyActivityRepository activityRepository,
                                       CurrentCompanyContext companyContext) {
        this.activityRepository = activityRepository;
        this.companyContext = companyContext;
    }

    @Transactional(readOnly = true)
    public ActivityCounts getCurrentCompanyCounts() {
        return activityRepository.findActivityCounts(companyContext.requireCompanyId());
    }

    @Transactional
    public ActivityCounts resetCurrentCompany() {
        Long companyId = companyContext.requireCompanyId();
        ActivityCounts deleted = activityRepository.findActivityCounts(companyId);
        activityRepository.resetActivity(companyId);
        return deleted;
    }

    @Transactional
    public BankReconciliationCounts resetBankReconciliationForCurrentCompany() {
        Long companyId = companyContext.requireCompanyId();
        BankReconciliationCounts deleted = activityRepository.findBankReconciliationCounts(companyId);
        activityRepository.resetBankReconciliation(companyId);
        return deleted;
    }
}
