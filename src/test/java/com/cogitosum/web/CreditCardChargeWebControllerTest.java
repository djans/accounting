package com.cogitosum.web;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.CreditCardCharge;
import com.cogitosum.entity.CreditCardChargeStatus;
import com.cogitosum.entity.Vendor;
import com.cogitosum.service.BillService;
import com.cogitosum.service.CreditCardChargeService;
import com.cogitosum.service.TaxRegime;
import com.cogitosum.service.VendorService;
import com.cogitosum.service.ChartOfAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CreditCardChargeWebController.class)
class CreditCardChargeWebControllerTest {
    @Autowired private MockMvc mockMvc;

    @MockitoBean private CreditCardChargeService chargeService;
    @MockitoBean private VendorService vendorService;
    @MockitoBean private ChartOfAccountService accountService;
    @MockitoBean private BillService billService;

    @Test
    void draftListShowsEditDeleteAndPostActions() throws Exception {
        CreditCardCharge charge = charge(CreditCardChargeStatus.DRAFT);
        when(chargeService.findAll()).thenReturn(List.of(charge));

        mockMvc.perform(get("/credit-card-charges"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Copy charge")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Edit draft")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Delete draft")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Post charge to the ledger")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"chargeIds\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Post selected drafts")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Delete selected drafts")));
    }

    @Test
    void postedChargesCannotBeSelectedForBulkActions() throws Exception {
        when(chargeService.findAll()).thenReturn(List.of(charge(CreditCardChargeStatus.POSTED)));

        mockMvc.perform(get("/credit-card-charges"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("disabled=\"disabled\"")));
    }

    @Test
    void newChargeFormProvidesDraftFields() throws Exception {
        when(vendorService.getAllVendors()).thenReturn(new ArrayList<>());
        when(accountService.getAccountsByType(any(AccountType.class))).thenReturn(new ArrayList<>());
        when(billService.getRegimes()).thenReturn(List.of(
                new TaxRegime("FED", "Federal GST", new BigDecimal("0.05"),
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new TaxRegime("QC", "Quebec GST + QST", new BigDecimal("0.05"),
                        BigDecimal.ZERO, new BigDecimal("0.09975"))));

        mockMvc.perform(get("/credit-card-charges/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("credit-card-charges/form"))
                .andExpect(model().attributeExists("charge", "vendors", "expenseAccounts",
                        "cardAccounts", "regimes"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Save draft")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Choose a tax type to see its breakdown.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-gst=\"0.05\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"gstTax\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"qstTax\"")));
    }

    @Test
    void copyChargePrefillsDetailsButLeavesDateBlank() throws Exception {
        Vendor vendor = new Vendor();
        vendor.setId(3L);
        vendor.setBusinessName("Office supplier");
        CreditCardCharge original = charge(CreditCardChargeStatus.POSTED);
        original.setVendor(vendor);
        original.setChargeDate(LocalDate.of(2026, 2, 1));
        original.setMemo("Paper and toner");
        original.setTaxRegime("QC");
        when(chargeService.findById(7L)).thenReturn(Optional.of(original));
        when(vendorService.getAllVendors()).thenReturn(List.of(vendor));
        when(accountService.getAccountsByType(any(AccountType.class)))
                .thenReturn(List.of(account(1L, "5000", "Office supplies"),
                        account(2L, "2100", "Credit card")));
        when(billService.getRegimes()).thenReturn(List.of(
                new TaxRegime("QC", "Quebec GST + QST", new BigDecimal("0.05"),
                        BigDecimal.ZERO, new BigDecimal("0.09975"))));

        mockMvc.perform(get("/credit-card-charges/7/copy"))
                .andExpect(status().isOk())
                .andExpect(view().name("credit-card-charges/form"))
                .andExpect(model().attribute("copyCharge", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Copy charge to a new draft")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"totalAmount\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"105.00\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paper and toner</textarea>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("2026-02-01"))));
    }

    private CreditCardCharge charge(CreditCardChargeStatus status) {
        CreditCardCharge charge = new CreditCardCharge();
        charge.setStatus(status);
        charge.setChargeDate(LocalDate.of(2026, 2, 1));
        charge.setVendor(new Vendor());
        charge.setExpenseAccount(account(1L, "5000", "Office supplies"));
        charge.setCardAccount(account(2L, "2100", "Credit card"));
        charge.setTotalAmount(new BigDecimal("105.00"));
        charge.setNetAmount(new BigDecimal("100.00"));
        charge.setTaxAmount(new BigDecimal("5.00"));
        return charge;
    }

    private ChartOfAccount account(Long id, String number, String name) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setAccountNumber(number);
        account.setAccountName(name);
        return account;
    }
}
