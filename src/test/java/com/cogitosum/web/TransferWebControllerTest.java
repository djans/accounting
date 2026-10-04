package com.cogitosum.web;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.entity.Transfer;
import com.cogitosum.entity.TransferStatus;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransferWebController.class)
class TransferWebControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TransferService transferService;
    @MockitoBean private ChartOfAccountService accountService;

    @Test
    void copyFormRetainsTransferDetailsButLeavesDateBlank() throws Exception {
        ChartOfAccount from = account(1L, "1000");
        ChartOfAccount to = account(2L, "1010");
        Transfer original = new Transfer();
        original.setId(12L);
        original.setFromAccount(from);
        original.setToAccount(to);
        original.setAmount(new BigDecimal("45.25"));
        original.setTransferDate(LocalDate.of(2026, 9, 30));
        original.setNotes("Monthly reserve");
        when(transferService.findById(12L)).thenReturn(Optional.of(original));
        when(accountService.getActiveAccounts()).thenReturn(List.of(from, to));

        var result = mockMvc.perform(get("/transfers/12/copy"))
                .andExpect(status().isOk())
                .andExpect(view().name("transfers/form"))
                .andExpect(model().attribute("copyTransfer", true))
                .andExpect(content().string(containsString("value=\"45.25\"")))
                .andExpect(content().string(containsString("Monthly reserve")))
                .andExpect(content().string(not(containsString("2026-09-30"))))
                .andReturn();

        Transfer copy = (Transfer) result.getModelAndView().getModel().get("transfer");
        assertNull(copy.getTransferDate());
        assertSame(from, copy.getFromAccount());
        assertSame(to, copy.getToAccount());
        assertEquals(new BigDecimal("45.25"), copy.getAmount());
        assertEquals("Monthly reserve", copy.getNotes());
    }

    @Test
    void createSavesDraftWithoutPosting() throws Exception {
        mockMvc.perform(post("/transfers")
                        .param("fromAccountId", "1")
                        .param("toAccountId", "2")
                        .param("amount", "45.25")
                        .param("transferDate", "2026-09-30")
                        .param("notes", "Reserve"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transfers"));

        verify(transferService).saveDraft(1L, 2L, new BigDecimal("45.25"),
                LocalDate.of(2026, 9, 30), "Reserve");
        verify(transferService, never()).postTransfer(anyLong(), anyString());
    }

    @Test
    void newTransferFormUsesActiveAccountsForDropdowns() throws Exception {
        ChartOfAccount active = account(1L, "1000");
        when(accountService.getActiveAccounts()).thenReturn(List.of(active));

        mockMvc.perform(get("/transfers/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("transfers/form"))
                .andExpect(content().string(containsString("value=\"1\"")))
                .andExpect(content().string(not(containsString("value=\"2\""))));

        verify(accountService).getActiveAccounts();
        verify(accountService, never()).getAllAccounts();
    }

    @Test
    void listShowsLifecycleActionsForDraftAndPostedTransfers() throws Exception {
        Transfer draft = transfer(TransferStatus.DRAFT, 11L);
        Transfer posted = transfer(TransferStatus.POSTED, 12L);
        when(transferService.getAllTransfers()).thenReturn(List.of(draft, posted));

        mockMvc.perform(get("/transfers"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit draft")))
                .andExpect(content().string(containsString("Delete draft")))
                .andExpect(content().string(containsString("Post transfer")))
                .andExpect(content().string(containsString("Reverse posted transfer")))
                .andExpect(content().string(containsString("Copy transfer")))
                .andExpect(content().string(containsString("View transfer")));

        mockMvc.perform(get("/transfers").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Comptabilisé")));
    }

    @Test
    void postedTransferDetailsShowJournalAndReconciliationState() throws Exception {
        Transfer posted = transfer(TransferStatus.POSTED, 15L);
        GeneralJournal journal = new GeneralJournal();
        journal.setJournalNumber("GJ-15");
        journal.setJournalDate(LocalDate.of(2026, 9, 8));
        journal.setReference("TRANSFER-15");
        journal.setStatus(JournalStatus.POSTED);
        journal.setPostedDate(LocalDateTime.of(2026, 10, 2, 11, 16));
        journal.setPostedBy("portal");

        JournalEntry bankEntry = new JournalEntry();
        bankEntry.setJournal(journal);
        bankEntry.setAccount(posted.getFromAccount());
        bankEntry.setDebit(BigDecimal.ZERO);
        bankEntry.setCredit(new BigDecimal("25000.00"));
        bankEntry.setLineNumber(2);
        bankEntry.setCleared(false);
        journal.setEntries(List.of(bankEntry));
        posted.setJournal(journal);
        when(transferService.findById(15L)).thenReturn(Optional.of(posted));

        mockMvc.perform(get("/transfers/15"))
                .andExpect(status().isOk())
                .andExpect(view().name("transfers/details"))
                .andExpect(content().string(containsString("TRANSFER-15")))
                .andExpect(content().string(containsString("GJ-15")))
                .andExpect(content().string(containsString("2026-10-02 11:16")))
                .andExpect(content().string(containsString("Not reconciled")))
                .andExpect(content().string(not(containsString(">POSTED<"))));
    }

    private Transfer transfer(TransferStatus status, Long id) {
        Transfer transfer = new Transfer();
        transfer.setId(id);
        transfer.setStatus(status);
        transfer.setFromAccount(account(1L, "1000"));
        transfer.setToAccount(account(2L, "1010"));
        transfer.setTransferDate(LocalDate.of(2026, 9, 30));
        transfer.setAmount(new BigDecimal("45.25"));
        return transfer;
    }

    private ChartOfAccount account(Long id, String number) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setAccountNumber(number);
        account.setAccountType(AccountType.ASSET);
        account.setAccountName("Account " + number);
        account.setActive(true);
        return account;
    }
}
