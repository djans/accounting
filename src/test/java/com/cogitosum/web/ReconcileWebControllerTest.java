package com.cogitosum.web;

import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.BankReconciliationSession;
import com.cogitosum.service.BankReconciliationService;
import com.cogitosum.service.BankReconciliationReportPdfService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@WebMvcTest(ReconcileWebController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReconcileWebControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BankReconciliationService reconciliationService;

    @MockitoBean
    private BankReconciliationReportPdfService reportPdfService;

    @Test
    void reconcileListsOnlyBankAccountsProvidedByTheService() throws Exception {
        ChartOfAccount account = bankAccount(1L);
        when(reconciliationService.getBankAccounts()).thenReturn(List.of(
                new BankReconciliationService.BankAccountSummary(account, new BigDecimal("125.00"))));

        mvc.perform(get("/reconcile"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("rows"))
                .andExpect(view().name("reconcile"));
    }

    @Test
    void reconcileAccountDisplaysJournalEntriesAndServerOpeningBalance() throws Exception {
        ChartOfAccount account = bankAccount(1L);
        GeneralJournal journal = new GeneralJournal();
        journal.setJournalDate(LocalDate.of(2026, 1, 30));
        journal.setJournalNumber("GJ-42");
        journal.setNarrative("Bank deposit");
        JournalEntry entry = new JournalEntry();
        entry.setId(20L);
        entry.setJournal(journal);
        entry.setDescription("Customer payment");
        entry.setDebit(new BigDecimal("25.00"));
        entry.setCredit(BigDecimal.ZERO);
        when(reconciliationService.getBankAccount(1L)).thenReturn(account);
        when(reconciliationService.getEligibleJournalEntries(1L)).thenReturn(List.of(entry));
        when(reconciliationService.getOpeningBalance(1L)).thenReturn(new BigDecimal("25.00"));

        mvc.perform(get("/reconcile/account/1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("account", account))
                .andExpect(model().attribute("journalEntries", List.of(entry)))
                .andExpect(model().attribute("openingBalance", new BigDecimal("25.00")))
                .andExpect(content().string(containsString("name=\"entryIds\"")))
                .andExpect(content().string(containsString("id=\"toggleAllEntries\"")))
                .andExpect(content().string(containsString("Customer payment")))
                .andExpect(content().string(not(containsString("Import bank statement CSV"))))
                .andExpect(view().name("reconcile_details"));
    }

    @Test
    void reportsListsCompletedSessionsForDownload() throws Exception {
        BankReconciliationSession session = session(31L);
        when(reportPdfService.getCompanySessions()).thenReturn(List.of(session));

        mvc.perform(get("/reconcile/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("reconcile_reports"))
                .andExpect(content().string(containsString("2026-09-30")))
                .andExpect(content().string(containsString("/reconcile/sessions/31/report.pdf")));
    }

    @Test
    void reportDownloadReturnsPdfAsAttachment() throws Exception {
        when(reportPdfService.getPdfReport(31L)).thenReturn(Optional.of(
                new BankReconciliationReportPdfService.PdfReport(
                        "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        "reconciliation-1001-2026-09-30.pdf")));

        mvc.perform(get("/reconcile/sessions/31/report.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        containsString("filename=reconciliation-1001-2026-09-30.pdf")));
    }

    @Test
    void finishParsesSelectedEntriesAndDelegatesServerValidation() throws Exception {
        when(reconciliationService.reconcile(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 1, 31)),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("100.00")),
                org.mockito.ArgumentMatchers.eq(List.of(20L, 21L))))
                .thenReturn(session(41L));

        mvc.perform(post("/reconcile/finish")
                        .param("accountId", "1")
                        .param("statementDate", "2026-01-31")
                        .param("statementEndingBalance", "100.00")
                        .param("entryIds", "20", "21"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reconcile/sessions/41/report.pdf"));

        verify(reconciliationService).reconcile(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 1, 31)),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("100.00")),
                org.mockito.ArgumentMatchers.eq(List.of(20L, 21L)));
    }

    private BankReconciliationSession session(Long id) {
        BankReconciliationSession session = new BankReconciliationSession();
        session.setId(id);
        session.setBankAccount(bankAccount(1L));
        session.setStatementDate(LocalDate.of(2026, 9, 30));
        session.setOpeningBalance(new BigDecimal("125.00"));
        session.setTransactionTotal(new BigDecimal("25.00"));
        session.setEndingBalance(new BigDecimal("150.00"));
        session.setCompletedAt(LocalDate.of(2026, 10, 2).atStartOfDay());
        return session;
    }

    private ChartOfAccount bankAccount(Long id) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setAccountNumber("1001");
        account.setAccountName("Bank");
        account.setCategory(AccountCategory.BANK);
        account.setActive(true);
        return account;
    }
}
