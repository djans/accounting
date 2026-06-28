package com.cogitosum;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@SpringBootTest
@Transactional
public class TransferServiceTest {

    @Autowired private com.cogitosum.service.ChartOfAccountService accountService;
    @Autowired private com.cogitosum.service.TransferService transferService;
    @Autowired private com.cogitosum.repository.GeneralLedgerRepository generalLedgerRepository;

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
        var transfer = transferService.createTransfer(savedFrom.getId(), savedTo.getId(), amount, LocalDate.now(), "Test transfer", "test-user");

        Assertions.assertNotNull(transfer.getId(), "Transfer should be persisted");
        Assertions.assertNotNull(transfer.getJournal(), "Transfer should reference a posted journal");

        var glTo = generalLedgerRepository.findByAccountId(savedTo.getId()).orElseThrow();
        var glFrom = generalLedgerRepository.findByAccountId(savedFrom.getId()).orElseThrow();

        Assertions.assertEquals(0, glTo.getDebitBalance().compareTo(amount), "Destination account should have debit = amount");
        Assertions.assertEquals(0, glFrom.getCreditBalance().compareTo(amount), "Source account should have credit = amount");
    }
}
