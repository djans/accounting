package com.cogitosum;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.time.LocalDate;

@SpringBootTest
@Transactional
public class TransferServiceTest {

    @Autowired private com.cogitosum.service.ChartOfAccountService accountService;
    @Autowired private com.cogitosum.service.TransferService transferService;
    @Autowired private com.cogitosum.repository.GeneralLedgerRepository generalLedgerRepository;

    @BeforeEach
    void authenticateCompanyUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test-admin@example.test", "ignored", java.util.List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void createTransfer_postsJournal_and_updatesGeneralLedger() {
        // Create two bank accounts
        ChartOfAccount from = new ChartOfAccount();
        from.setAccountNumber("1001");
        from.setAccountName("Bank A");
        from.setAccountType(AccountType.ASSET);
        from.setDescription("Operating bank A");
        from.setActive(true);
        ChartOfAccount savedFrom = accountService.createAccount(from);

        ChartOfAccount to = new ChartOfAccount();
        to.setAccountNumber("1002");
        to.setAccountName("Bank B");
        to.setAccountType(AccountType.ASSET);
        to.setDescription("Operating bank B");
        to.setActive(true);
        ChartOfAccount savedTo = accountService.createAccount(to);

        BigDecimal amount = new BigDecimal("1234.56");
        var transfer = transferService.saveDraft(
                savedFrom.getId(), savedTo.getId(), amount, LocalDate.now(), "Test transfer");

        Assertions.assertNotNull(transfer.getId(), "Transfer should be persisted");
        Assertions.assertEquals(com.cogitosum.entity.TransferStatus.DRAFT, transfer.getStatus());
        Assertions.assertNull(transfer.getJournal(), "Draft transfer should not post a journal");

        transfer = transferService.postTransfer(transfer.getId(), "test-user");

        Assertions.assertEquals(com.cogitosum.entity.TransferStatus.POSTED, transfer.getStatus());
        Assertions.assertNotNull(transfer.getJournal(), "Posted transfer should reference its journal");

        var glTo = generalLedgerRepository.findByAccountId(savedTo.getId()).orElseThrow();
        var glFrom = generalLedgerRepository.findByAccountId(savedFrom.getId()).orElseThrow();

        Assertions.assertEquals(0, glTo.getDebitBalance().compareTo(amount), "Destination account should have debit = amount");
        Assertions.assertEquals(0, glFrom.getCreditBalance().compareTo(amount), "Source account should have credit = amount");
    }

    @Test
    public void getAllTransfers_returnsNewestTransferDateFirst() {
        ChartOfAccount from = createBankAccount("1101");
        ChartOfAccount to = createBankAccount("1102");
        var older = transferService.saveDraft(from.getId(), to.getId(), new BigDecimal("10.00"),
                LocalDate.of(2026, 9, 1), "Older");
        var newer = transferService.saveDraft(from.getId(), to.getId(), new BigDecimal("20.00"),
                LocalDate.of(2026, 9, 30), "Newer");

        var transfers = transferService.getAllTransfers();

        Assertions.assertEquals(newer.getId(), transfers.get(0).getId());
        Assertions.assertEquals(older.getId(), transfers.get(1).getId());
    }

    private ChartOfAccount createBankAccount(String accountNumber) {
        ChartOfAccount account = new ChartOfAccount();
        account.setAccountNumber(accountNumber);
        account.setAccountName("Bank " + accountNumber);
        account.setAccountType(AccountType.ASSET);
        account.setDescription("Operating bank account");
        account.setActive(true);
        return accountService.createAccount(account);
    }
}
