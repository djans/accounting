package com.cogitosum.service;

import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.repository.GeneralLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class GeneralLedgerService {

    @Autowired
    private GeneralLedgerRepository generalLedgerRepository;

    public GeneralLedger createLedgerAccount(ChartOfAccount account) {
        GeneralLedger ledger = new GeneralLedger();
        ledger.setAccount(account);
        ledger.setDebitBalance(BigDecimal.ZERO);
        ledger.setCreditBalance(BigDecimal.ZERO);
        ledger.setBalance(BigDecimal.ZERO);
        return generalLedgerRepository.save(ledger);
    }

    public Optional<GeneralLedger> getLedgerByAccountId(Long accountId) {
        return generalLedgerRepository.findByAccountId(accountId);
    }

    public List<GeneralLedger> getAllLedgerAccounts() {
        return generalLedgerRepository.findAll();
    }

    public GeneralLedger updateBalance(Long accountId, BigDecimal debitAmount, BigDecimal creditAmount) {
        Optional<GeneralLedger> ledger = generalLedgerRepository.findByAccountId(accountId);
        if (ledger.isPresent()) {
            GeneralLedger gl = ledger.get();
            gl.setDebitBalance(gl.getDebitBalance().add(debitAmount));
            gl.setCreditBalance(gl.getCreditBalance().add(creditAmount));
            return generalLedgerRepository.save(gl);
        }
        return null;
    }
}

