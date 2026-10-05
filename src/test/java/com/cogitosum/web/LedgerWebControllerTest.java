package com.cogitosum.web;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.service.GeneralLedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerWebControllerTest {

    @Test
    void listsLedgerAccountsAndAccountTypesForFiltering() {
        GeneralLedgerService ledgerService = mock(GeneralLedgerService.class);
        when(ledgerService.getAllLedgerAccounts()).thenReturn(List.of());
        LedgerWebController controller = new LedgerWebController();
        ReflectionTestUtils.setField(controller, "generalLedgerService", ledgerService);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.list(model);

        assertEquals("ledger/list", view);
        assertEquals(List.of(), model.get("ledgers"));
        assertArrayEquals(AccountType.values(), (AccountType[]) model.get("accountTypes"));
    }

    @Test
    void selectsAnAccountAndLoadsItsOwnLedgerBalances() {
        GeneralLedgerService ledgerService = mock(GeneralLedgerService.class);
        ChartOfAccount account = new ChartOfAccount();
        account.setId(12L);
        GeneralLedger ledger = new GeneralLedger();
        ledger.setAccount(account);
        when(ledgerService.getLedgerByAccountId(12L)).thenReturn(Optional.of(ledger));
        when(ledgerService.getAllLedgerAccounts()).thenReturn(List.of(ledger));

        LedgerWebController controller = new LedgerWebController();
        ReflectionTestUtils.setField(controller, "generalLedgerService", ledgerService);
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.selectAccount(12L, model, redirectAttributes);

        assertEquals("ledger/form", view);
        assertEquals(ledger, model.get("ledger"));
        assertEquals(List.of(ledger), model.get("ledgerAccounts"));
    }
}
