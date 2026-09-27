package com.cogitosum.service;

import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class GeneralLedgerService {

    @Autowired
    private GeneralLedgerRepository generalLedgerRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private CurrentCompanyContext companyContext;

    public GeneralLedger createLedgerAccount(ChartOfAccount account) {
        Long companyId = companyContext.requireCompanyId();
        ChartOfAccount ownedAccount = chartOfAccountRepository.findByIdAndCompanyId(account.getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        GeneralLedger ledger = new GeneralLedger();
        ledger.setCompany(companyContext.requireCompany());
        ledger.setAccount(ownedAccount);
        ledger.setDebitBalance(BigDecimal.ZERO);
        ledger.setCreditBalance(BigDecimal.ZERO);
        ledger.setBalance(BigDecimal.ZERO);
        return generalLedgerRepository.save(ledger);
    }

    public Optional<GeneralLedger> getLedgerByAccountId(Long accountId) {
        return generalLedgerRepository.findByCompanyIdAndAccountId(companyContext.requireCompanyId(), accountId);
    }

    public List<GeneralLedger> getAllLedgerAccounts() {
        return generalLedgerRepository.findAllByCompanyIdOrderByAccountAccountNumberAsc(companyContext.requireCompanyId());
    }

    public GeneralLedger getLedger(Long id) {
        return generalLedgerRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Ledger account not found: " + id));
    }

    public GeneralLedger updateLedger(Long id, Long accountId, BigDecimal debitAmount, BigDecimal creditAmount) {
        GeneralLedger ledger = getLedger(id);
        Long companyId = companyContext.requireCompanyId();
        ChartOfAccount account = chartOfAccountRepository.findByIdAndCompanyId(accountId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        generalLedgerRepository.findByCompanyIdAndAccountId(companyId, accountId)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("That account already has a General Ledger entry");
                });
        ledger.setAccount(account);
        ledger.setDebitBalance(nonNegative(debitAmount));
        ledger.setCreditBalance(nonNegative(creditAmount));
        return generalLedgerRepository.save(ledger);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Debit and credit amounts must be zero or positive");
        }
        return value;
    }

    public GeneralLedger updateBalance(Long accountId, BigDecimal debitAmount, BigDecimal creditAmount) {
        Optional<GeneralLedger> ledger = generalLedgerRepository.findByCompanyIdAndAccountId(
                companyContext.requireCompanyId(), accountId);
        if (ledger.isPresent()) {
            GeneralLedger gl = ledger.get();
            gl.setDebitBalance(gl.getDebitBalance().add(debitAmount));
            gl.setCreditBalance(gl.getCreditBalance().add(creditAmount));
            return generalLedgerRepository.save(gl);
        }
        return null;
    }
}
