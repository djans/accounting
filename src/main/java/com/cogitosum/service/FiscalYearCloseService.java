package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.FiscalYearRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Clôture d'exercice : vire les soldes des comptes de résultats (REVENUE/EXPENSE) vers
 * les bénéfices non répartis (3100) au moyen d'une écriture de clôture, puis verrouille
 * l'exercice. La réouverture contrepasse l'écriture.
 *
 * <p>Limite assumée : {@link AccountingReportService} expose des soldes cumulés non bornés
 * par date ; la clôture vire donc les soldes <b>courants</b> des comptes de résultats. C'est
 * correct si l'exercice est clôturé une fois et que ces comptes étaient à zéro en début
 * d'exercice (état normal après la clôture précédente).
 */
@Service
public class FiscalYearCloseService {

    public static final String RETAINED_EARNINGS = "3100"; // Bénéfices non répartis

    @Autowired
    private FiscalYearRepository fiscalYearRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private GeneralLedgerRepository ledgerRepository;

    @Autowired
    private GeneralJournalService journalService;

    @Autowired
    private AccountingReportService reportService;

    @Transactional
    public FiscalYear close(Long fiscalYearId, String postedBy) {
        FiscalYear fy = fiscalYearRepository.findById(fiscalYearId)
            .orElseThrow(() -> new IllegalArgumentException("Fiscal year not found: " + fiscalYearId));
        if (fy.getStatus() == FiscalYearStatus.CLOSED) {
            throw new IllegalStateException("L'exercice est déjà clôturé");
        }

        Map<String, Object> trialBalance = reportService.getTrialBalance();
        if (Boolean.FALSE.equals(trialBalance.get("balanced"))) {
            throw new IllegalStateException("La balance de vérification n'est pas équilibrée — clôture impossible");
        }

        ChartOfAccount retainedEarnings = lookup(RETAINED_EARNINGS);

        List<JournalEntry> entries = new ArrayList<>();
        int line = 1;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        // Dr each revenue account by its (credit-normal) balance to zero it out.
        for (ChartOfAccount acct : accountRepository.findByAccountTypeOrderByAccountNumberAsc(AccountType.REVENUE)) {
            BigDecimal bal = balanceOf(acct);
            if (bal.signum() == 0) continue;
            entries.add(entry(acct, bal, BigDecimal.ZERO, "Clôture — " + acct.getAccountName(), line++));
            totalRevenue = totalRevenue.add(bal);
        }
        // Cr each expense account by its (debit-normal) balance to zero it out.
        for (ChartOfAccount acct : accountRepository.findByAccountTypeOrderByAccountNumberAsc(AccountType.EXPENSE)) {
            BigDecimal bal = balanceOf(acct);
            if (bal.signum() == 0) continue;
            entries.add(entry(acct, BigDecimal.ZERO, bal, "Clôture — " + acct.getAccountName(), line++));
            totalExpenses = totalExpenses.add(bal);
        }

        BigDecimal netIncome = totalRevenue.subtract(totalExpenses);
        if (netIncome.signum() > 0) {
            entries.add(entry(retainedEarnings, BigDecimal.ZERO, netIncome, "Résultat net viré aux BNR", line++));
        } else if (netIncome.signum() < 0) {
            entries.add(entry(retainedEarnings, netIncome.negate(), BigDecimal.ZERO, "Perte nette virée aux BNR", line++));
        }

        if (!entries.isEmpty()) {
            GeneralJournal jl = new GeneralJournal();
            // Dated on the last day of the exercise; posted while the year is still OPEN.
            jl.setJournalDate(fy.getEndDate());
            jl.setNarrative("Clôture de l'exercice " + fy.getLabel());
            jl.setReference("FY-CLOSE-" + fy.getId());
            jl.setEntries(entries);

            GeneralJournal saved = journalService.createJournal(jl);
            GeneralJournal posted = journalService.postJournal(saved.getId(), postedBy);
            fy.setClosingJournal(posted);
        }

        fy.setNetIncome(netIncome);
        fy.setStatus(FiscalYearStatus.CLOSED);
        fy.setClosedDate(LocalDate.now());
        fy.setClosedBy(postedBy);
        return fiscalYearRepository.save(fy);
    }

    @Transactional
    public FiscalYear reopen(Long fiscalYearId) {
        FiscalYear fy = fiscalYearRepository.findById(fiscalYearId)
            .orElseThrow(() -> new IllegalArgumentException("Fiscal year not found: " + fiscalYearId));
        if (fy.getStatus() != FiscalYearStatus.CLOSED) {
            throw new IllegalStateException("Seul un exercice clôturé peut être rouvert");
        }

        // Unlock first so the reversal journal is allowed to post into the period.
        fy.setStatus(FiscalYearStatus.OPEN);
        fiscalYearRepository.save(fy);

        if (fy.getClosingJournal() != null) {
            journalService.reverseJournal(fy.getClosingJournal().getId(),
                "Réouverture de l'exercice " + fy.getLabel());
        }
        fy.setClosingJournal(null);
        fy.setClosedDate(null);
        fy.setClosedBy(null);
        fy.setNetIncome(null);
        return fiscalYearRepository.save(fy);
    }

    private BigDecimal balanceOf(ChartOfAccount acct) {
        return ledgerRepository.findByAccountId(acct.getId())
            .map(GeneralLedger::getBalance)
            .orElse(BigDecimal.ZERO);
    }

    private JournalEntry entry(ChartOfAccount acct, BigDecimal debit, BigDecimal credit, String desc, int line) {
        JournalEntry e = new JournalEntry();
        e.setAccount(acct);
        e.setDebit(debit);
        e.setCredit(credit);
        e.setDescription(desc);
        e.setLineNumber(line);
        return e;
    }

    private ChartOfAccount lookup(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new IllegalStateException("Required account not seeded: " + accountNumber));
    }
}
