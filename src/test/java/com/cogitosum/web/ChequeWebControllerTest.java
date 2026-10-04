package com.cogitosum.web;

import com.cogitosum.service.*;
import com.cogitosum.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChequeWebController.class)
public class ChequeWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChequeService chequeService;

    @MockitoBean
    private VendorService vendorService;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private ChartOfAccountService accountService;

    @MockitoBean
    private TaxCodeService taxCodeService;

    @Test
    public void newCheque_ReturnsViewWithAttributes() throws Exception {
        when(vendorService.getAllVendors()).thenReturn(new ArrayList<>());
        when(customerService.getAllCustomers()).thenReturn(new ArrayList<>());
        when(accountService.getAllAccounts()).thenReturn(new ArrayList<>());
        when(taxCodeService.getActiveCodes()).thenReturn(new ArrayList<>());
        when(chequeService.nextNumber()).thenReturn("1001");

        mockMvc.perform(get("/cheques/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("cheques/form"))
                .andExpect(model().attributeExists("vendors", "customers", "accounts", "bankAccounts", "taxCodes", "nextChequeNumber", "cheque"))
                .andExpect(model().attribute("cheque", org.hamcrest.Matchers.instanceOf(WrittenCheque.class)));
    }

    @Test
    public void listDisplaysChequeLifecycleStatusAndActions() throws Exception {
        WrittenCheque cheque = new WrittenCheque();
        cheque.setStatus(WrittenChequeStatus.ISSUED);
        cheque.setChequeNumber("101");
        cheque.setChequeDate(java.time.LocalDate.of(2026, 1, 15));
        cheque.setAmount(new java.math.BigDecimal("25.00"));
        Vendor vendor = new Vendor();
        vendor.setBusinessName("Test Vendor");
        cheque.setVendor(vendor);
        when(chequeService.findAll()).thenReturn(java.util.List.of(cheque));

        mockMvc.perform(get("/cheques"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Issued")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Copy cheque")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Void cheque")));
    }

    @Test
    public void listDisplaysDeleteActionOnlyForDraftCheques() throws Exception {
        WrittenCheque draft = new WrittenCheque();
        draft.setStatus(WrittenChequeStatus.DRAFT);
        draft.setChequeNumber("102");
        draft.setChequeDate(java.time.LocalDate.of(2026, 1, 15));
        draft.setAmount(new java.math.BigDecimal("25.00"));
        draft.setVendor(new Vendor());
        when(chequeService.findAll()).thenReturn(java.util.List.of(draft));

        mockMvc.perform(get("/cheques"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Delete draft")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Delete this cheque draft?")));
    }

    @Test
    public void reissueFormCopiesDetailsFromVoidedCheque() throws Exception {
        WrittenCheque original = new WrittenCheque();
        original.setStatus(WrittenChequeStatus.VOIDED);
        original.setChequeNumber("101");
        Vendor vendor = new Vendor();
        ChartOfAccount bank = new ChartOfAccount();
        ChartOfAccount expenseAccount = new ChartOfAccount();
        original.setVendor(vendor);
        original.setBankAccount(bank);
        ChequeExpense sourceExpense = new ChequeExpense();
        sourceExpense.setAccount(expenseAccount);
        sourceExpense.setAmount(new java.math.BigDecimal("25.00"));
        original.setExpenses(new ArrayList<>(java.util.List.of(sourceExpense)));
        when(chequeService.getVoidedForReissue(10L)).thenReturn(original);
        when(chequeService.nextNumber()).thenReturn("102");
        when(vendorService.getAllVendors()).thenReturn(new ArrayList<>());
        when(customerService.getAllCustomers()).thenReturn(new ArrayList<>());
        when(accountService.getAllAccounts()).thenReturn(new ArrayList<>());
        when(taxCodeService.getActiveCodes()).thenReturn(new ArrayList<>());

        var result = mockMvc.perform(get("/cheques/new").param("reissueOfId", "10"))
                .andExpect(status().isOk())
                .andReturn();
        WrittenCheque draft = (WrittenCheque) result.getModelAndView().getModel().get("cheque");

        assertSame(original, draft.getReissueOf());
        assertSame(vendor, draft.getVendor());
        assertSame(bank, draft.getBankAccount());
        assertEquals(new java.math.BigDecimal("25.00"), draft.getExpenses().get(0).getAmount());
    }

    @Test
    public void copyFormCopiesDetailsButLeavesDateAndNumberForTheNewCheque() throws Exception {
        WrittenCheque original = new WrittenCheque();
        original.setStatus(WrittenChequeStatus.ISSUED);
        original.setChequeNumber("101");
        original.setChequeDate(java.time.LocalDate.of(2026, 1, 15));
        original.setMemo("Office supplies");
        Vendor vendor = new Vendor();
        ChartOfAccount bank = new ChartOfAccount();
        ChartOfAccount expenseAccount = new ChartOfAccount();
        original.setVendor(vendor);
        original.setBankAccount(bank);
        ChequeExpense sourceExpense = new ChequeExpense();
        sourceExpense.setAccount(expenseAccount);
        sourceExpense.setTax("GST");
        sourceExpense.setAmount(new java.math.BigDecimal("25.00"));
        sourceExpense.setMemo("Paper");
        original.setExpenses(new ArrayList<>(List.of(sourceExpense)));
        when(chequeService.findById(10L)).thenReturn(Optional.of(original));
        when(chequeService.nextNumber()).thenReturn("102");
        when(vendorService.getAllVendors()).thenReturn(new ArrayList<>());
        when(customerService.getAllCustomers()).thenReturn(new ArrayList<>());
        when(accountService.getAllAccounts()).thenReturn(new ArrayList<>());
        when(taxCodeService.getActiveCodes()).thenReturn(new ArrayList<>());

        var result = mockMvc.perform(get("/cheques/10/copy"))
                .andExpect(status().isOk())
                .andExpect(view().name("cheques/form"))
                .andExpect(model().attribute("copyCheque", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Copy cheque to a new draft")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"102\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("2026-01-15"))))
                .andReturn();

        WrittenCheque copy = (WrittenCheque) result.getModelAndView().getModel().get("cheque");
        assertNull(copy.getChequeDate());
        assertNull(copy.getChequeNumber());
        assertSame(vendor, copy.getVendor());
        assertSame(bank, copy.getBankAccount());
        assertEquals("Office supplies", copy.getMemo());
        assertEquals(1, copy.getExpenses().size());
        assertEquals("GST", copy.getExpenses().get(0).getTax());
        assertEquals(new java.math.BigDecimal("25.00"), copy.getExpenses().get(0).getAmount());
        assertNotSame(sourceExpense, copy.getExpenses().get(0));
    }

    @Test
    public void chequeFormProvidesPurchaseTaxRatesForTheGrossAmountPreview() throws Exception {
        TaxItem item = new TaxItem();
        item.setRate(new java.math.BigDecimal("0.05000"));
        item.setForPurchases(true);
        TaxGroup group = new TaxGroup();
        group.setTaxItems(java.util.List.of(item));
        TaxCode taxCode = new TaxCode();
        taxCode.setCode("GST");
        taxCode.setName("Goods and Services Tax");
        taxCode.setPurchaseTaxGroup(group);
        when(taxCodeService.getActiveCodes()).thenReturn(java.util.List.of(taxCode));
        when(vendorService.getAllVendors()).thenReturn(new ArrayList<>());
        when(customerService.getAllCustomers()).thenReturn(new ArrayList<>());
        when(accountService.getAllAccounts()).thenReturn(new ArrayList<>());
        when(chequeService.nextNumber()).thenReturn("1001");

        mockMvc.perform(get("/cheques/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-rates=\"0.05000\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("GST - Goods and Services Tax")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("addEventListener('blur', updateTotal)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cheque total (taxes included)")));
    }
}
