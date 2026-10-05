package com.cogitosum.service;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.ChequeExpense;
import com.cogitosum.entity.CreditCardCharge;
import com.cogitosum.entity.CreditCardChargeStatus;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.TaxCode;
import com.cogitosum.entity.TaxAgency;
import com.cogitosum.entity.TaxFilingPeriod;
import com.cogitosum.entity.TaxGroup;
import com.cogitosum.entity.TaxItem;
import com.cogitosum.entity.Vendor;
import com.cogitosum.entity.WrittenCheque;
import com.cogitosum.entity.WrittenChequeStatus;
import com.cogitosum.dto.TaxReturnLineDTO;
import com.cogitosum.dto.TaxReturnLineDetailDTO;
import com.cogitosum.dto.TaxReturnRowDTO;
import com.cogitosum.repository.BillRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.CreditCardChargeRepository;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.TaxFilingPeriodRepository;
import com.cogitosum.repository.WrittenChequeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaxFilingServiceJournalTaxTest {

    private TaxFilingService service;
    private GeneralJournalService journalService;
    private TaxAgency agency;
    private TaxItem purchaseItem;
    private ChartOfAccount itcAccount;
    private InvoiceRepository invoiceRepository;
    private CreditCardChargeRepository creditCardChargeRepository;
    private WrittenChequeRepository chequeRepository;
    private ChartOfAccountRepository accountRepository;
    private TaxFilingPeriodRepository periodRepository;
    private TaxCodeService taxCodeService;
    private LocalDate start;
    private LocalDate end;

    @BeforeEach
    void setUp() {
        service = new TaxFilingService();
        journalService = mock(GeneralJournalService.class);
        ReflectionTestUtils.setField(service, "journalService", journalService);
        ReflectionTestUtils.setField(service, "companyContext", mockCompanyContext());
        periodRepository = mock(TaxFilingPeriodRepository.class);
        ReflectionTestUtils.setField(service, "periodRepository", periodRepository);

        BillRepository billRepository = mock(BillRepository.class);
        when(billRepository.findByCompanyIdAndBillDateBetweenOrderByBillDateDesc(eq(1L), any(), any()))
                .thenReturn(List.of());
        ReflectionTestUtils.setField(service, "billRepository", billRepository);

        creditCardChargeRepository = mock(CreditCardChargeRepository.class);
        when(creditCardChargeRepository.findByCompanyIdAndChargeDateBetweenOrderByChargeDateDescIdDesc(
                eq(1L), any(), any())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "creditCardChargeRepository", creditCardChargeRepository);
        chequeRepository = mock(WrittenChequeRepository.class);
        when(chequeRepository.findByCompanyIdAndChequeDateBetweenOrderByChequeDateDescIdDesc(
                eq(1L), any(), any())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "chequeRepository", chequeRepository);
        BillService billService = mock(BillService.class);
        when(billService.getRegimes()).thenReturn(InvoiceService.REGIMES);
        ReflectionTestUtils.setField(service, "billService", billService);

        invoiceRepository = mock(InvoiceRepository.class);
        when(invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(eq(1L), any(), any()))
                .thenReturn(List.of());
        ReflectionTestUtils.setField(service, "invoiceRepository", invoiceRepository);
        accountRepository = mock(ChartOfAccountRepository.class);
        ReflectionTestUtils.setField(service, "accountRepository", accountRepository);
        taxCodeService = mock(TaxCodeService.class);
        when(taxCodeService.getItemsByAgency(10L)).thenReturn(List.of());
        when(taxCodeService.getAllCodes()).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "taxCodeService", taxCodeService);

        agency = new TaxAgency();
        agency.setId(10L);
        agency.setCode(TaxFilingService.CRA_CODE);

        itcAccount = new ChartOfAccount();
        itcAccount.setId(20L);
        itcAccount.setAccountType(AccountType.ASSET);
        purchaseItem = new TaxItem();
        purchaseItem.setId(30L);
        purchaseItem.setCode("GST-ITC");
        purchaseItem.setRate(new BigDecimal("0.05000"));
        purchaseItem.setForPurchases(true);
        purchaseItem.setForSales(true);
        purchaseItem.setAgency(agency);
        purchaseItem.setItcAccount(itcAccount);

        start = LocalDate.of(2026, 1, 1);
        end = LocalDate.of(2026, 1, 31);
    }

    @Test
    void rejectsInactiveBankAccountForTaxPayment() {
        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setId(45L);
        period.setStatus(com.cogitosum.entity.TaxFilingStatus.FILED);
        period.setNetOwing(new BigDecimal("100.00"));
        period.setAgency(agency);
        when(periodRepository.findByIdAndCompanyId(45L, 1L)).thenReturn(Optional.of(period));

        ChartOfAccount bank = new ChartOfAccount();
        bank.setAccountNumber("1000");
        bank.setAccountType(AccountType.ASSET);
        bank.setCategory(AccountCategory.BANK);
        bank.setActive(false);
        when(accountRepository.findByCompanyIdAndAccountNumber(1L, "1000")).thenReturn(Optional.of(bank));

        assertThrows(IllegalArgumentException.class,
                () -> service.recordPayment(45L, "1000", start, "portal"));
        verifyNoInteractions(journalService);
    }

    @Test
    void includesTaxableJournalPurchaseBaseInItcTotal() {
        when(journalService.getJournalsByDateRange(start, end)).thenReturn(List.of(
                journal(JournalStatus.POSTED, new BigDecimal("100.00"), BigDecimal.ZERO, false)));

        assertEquals(new BigDecimal("5.00"), service.sumItc(agency, start, end));
    }

    @Test
    void usesJournalAmountDirectlyWhenEntryIsOnTheItcAccount() {
        when(journalService.getJournalsByDateRange(start, end)).thenReturn(List.of(
                journal(JournalStatus.POSTED, new BigDecimal("26.73"), BigDecimal.ZERO, true)));

        assertEquals(new BigDecimal("26.73"), service.sumItc(agency, start, end));
    }

    @Test
    void reversedJournalAndItsPostedReversalNetToZeroInItcTotal() {
        GeneralJournal original = journal(JournalStatus.REVERSED, new BigDecimal("100.00"), BigDecimal.ZERO, false);
        GeneralJournal reversal = journal(JournalStatus.POSTED, BigDecimal.ZERO, new BigDecimal("100.00"), false);
        when(journalService.getJournalsByDateRange(start, end)).thenReturn(List.of(original, reversal));

        assertEquals(new BigDecimal("0.00"), service.sumItc(agency, start, end));
    }

    @Test
    void includesTaxableJournalSalesBaseInCollectedTotal() {
        ChartOfAccount revenue = new ChartOfAccount();
        revenue.setAccountType(AccountType.REVENUE);
        GeneralJournal sale = new GeneralJournal();
        sale.setStatus(JournalStatus.POSTED);
        sale.setJournalDate(start);
        JournalEntry entry = new JournalEntry();
        entry.setJournal(sale);
        entry.setAccount(revenue);
        entry.setCredit(new BigDecimal("200.00"));
        entry.setDebit(BigDecimal.ZERO);
        entry.setTaxItem(purchaseItem);
        sale.setEntries(List.of(entry));
        when(journalService.getJournalsByDateRange(start, end)).thenReturn(List.of(sale));

        assertEquals(new BigDecimal("10.00"), service.sumTaxCollected(agency, start, end));
    }

    @Test
    void explainsSalesTaxReturnLineWithItsInvoiceSource() {
        TaxItem salesItem = new TaxItem();
        salesItem.setId(31L);
        salesItem.setCode("GST");
        salesItem.setName("GST 5%");
        salesItem.setRate(new BigDecimal("0.05000"));
        salesItem.setForSales(true);
        salesItem.setSalesReturnLine("103");
        salesItem.setAgency(agency);
        TaxGroup salesGroup = new TaxGroup();
        salesGroup.setTaxItems(List.of(salesItem));
        TaxCode taxCode = new TaxCode();
        taxCode.setCode("FED");
        taxCode.setSalesTaxGroup(salesGroup);
        when(taxCodeService.getItemsByAgency(10L)).thenReturn(List.of(salesItem));
        when(taxCodeService.getAllCodes()).thenReturn(List.of(taxCode));

        Invoice invoice = new Invoice();
        invoice.setId(71L);
        invoice.setInvoiceNumber("INV-71");
        invoice.setInvoiceDate(start);
        invoice.setTaxRegime("FED");
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setGstAmount(new BigDecimal("5.00"));
        invoice.setHstAmount(BigDecimal.ZERO);
        invoice.setQstAmount(BigDecimal.ZERO);
        when(invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                eq(1L), eq(start), eq(end))).thenReturn(List.of(invoice));
        when(journalService.getJournalsByDateRange(start, end)).thenReturn(List.of());

        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setAgency(agency);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);

        List<TaxReturnLineDetailDTO> details = service.getReturnLineDetails(period, "103");

        assertEquals(1, details.size());
        assertEquals("INVOICE", details.get(0).sourceType());
        assertEquals("INV-71", details.get(0).documentNumber());
        assertEquals(new BigDecimal("5.00"), details.get(0).amount());

        List<TaxReturnLineDetailDTO> totalDetails = service.getReturnLineDetails(period, "105");
        assertEquals(new BigDecimal("5.00"), totalDetails.stream()
                .map(TaxReturnLineDetailDTO::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void includesCardChargeAndChequeTaxInItcReturnLineAndDetails() {
        TaxItem purchaseTaxItem = new TaxItem();
        purchaseTaxItem.setCode("GST");
        purchaseTaxItem.setName("GST 5%");
        purchaseTaxItem.setRate(new BigDecimal("0.05000"));
        purchaseTaxItem.setForPurchases(true);
        purchaseTaxItem.setPurchaseReturnLine("106");
        purchaseTaxItem.setAgency(agency);
        TaxGroup purchaseGroup = new TaxGroup();
        purchaseGroup.setTaxItems(List.of(purchaseTaxItem));
        TaxCode taxCode = new TaxCode();
        taxCode.setCode("FED");
        taxCode.setPurchaseTaxGroup(purchaseGroup);
        when(taxCodeService.getItemsByAgency(10L)).thenReturn(List.of(purchaseTaxItem));
        when(taxCodeService.getAllCodes()).thenReturn(List.of(taxCode));

        CreditCardCharge charge = new CreditCardCharge();
        charge.setChargeDate(start);
        charge.setTaxRegime("FED");
        charge.setNetAmount(new BigDecimal("100.00"));
        charge.setTaxAmount(new BigDecimal("5.00"));
        charge.setStatus(CreditCardChargeStatus.POSTED);
        when(creditCardChargeRepository.findByCompanyIdAndChargeDateBetweenOrderByChargeDateDescIdDesc(
                eq(1L), eq(start), eq(end))).thenReturn(List.of(charge));

        ChequeExpense expense = new ChequeExpense();
        expense.setTax("FED");
        expense.setAmount(new BigDecimal("100.00"));
        WrittenCheque cheque = new WrittenCheque();
        cheque.setChequeNumber("CHK-82");
        cheque.setChequeDate(start);
        cheque.setStatus(WrittenChequeStatus.ISSUED);
        cheque.setExpenses(List.of(expense));
        when(chequeRepository.findByCompanyIdAndChequeDateBetweenOrderByChequeDateDescIdDesc(
                eq(1L), eq(start), eq(end))).thenReturn(List.of(cheque));
        when(journalService.getJournalsByDateRange(start, end)).thenReturn(List.of());

        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setAgency(agency);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);

        assertEquals(List.of(new TaxReturnLineDTO("PURCHASES", "106", new BigDecimal("10.00"))),
                service.getReturnLineBreakdown(period));
        List<TaxReturnLineDetailDTO> details = service.getReturnLineDetails(period, "106");
        assertEquals(List.of("CREDIT_CARD_CHARGE", "CHEQUE"),
                details.stream().map(TaxReturnLineDetailDTO::sourceType).toList());
        assertEquals(new BigDecimal("100.00"), details.get(0).taxableAmount());
        assertEquals(new BigDecimal("0.05000"), details.get(0).rate());
        assertEquals(new BigDecimal("10.00"), details.stream()
                .map(TaxReturnLineDetailDTO::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void groupsInvoiceTaxesByConfiguredSalesReturnLine() {
        TaxItem salesItem = new TaxItem();
        salesItem.setId(31L);
        salesItem.setCode("GST");
        salesItem.setRate(new BigDecimal("0.05000"));
        salesItem.setForSales(true);
        salesItem.setForPurchases(true);
        salesItem.setAgency(agency);
        salesItem.setSalesReturnLine("103");

        TaxGroup salesGroup = new TaxGroup();
        salesGroup.setTaxItems(List.of(salesItem));
        TaxCode taxCode = new TaxCode();
        taxCode.setCode("FED");
        taxCode.setSalesTaxGroup(salesGroup);
        when(taxCodeService.getItemsByAgency(10L)).thenReturn(List.of(salesItem));
        when(taxCodeService.getAllCodes()).thenReturn(List.of(taxCode));

        Invoice invoice = new Invoice();
        invoice.setTaxRegime("FED");
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setGstAmount(new BigDecimal("5.00"));
        invoice.setHstAmount(BigDecimal.ZERO);
        invoice.setQstAmount(BigDecimal.ZERO);
        when(invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                eq(1L), eq(start), eq(end))).thenReturn(List.of(invoice));
        when(journalService.getJournalsByDateRange(start, end)).thenReturn(List.of());

        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setAgency(agency);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);

        assertEquals(List.of(new TaxReturnLineDTO("SALES", "103", new BigDecimal("5.00"))),
                service.getReturnLineBreakdown(period));
    }

    @Test
    void buildsCalculatedReturnRowsFromSalesAndTaxLines() {
        TaxItem salesItem = new TaxItem();
        salesItem.setId(33L);
        salesItem.setCode("GST");
        salesItem.setRate(new BigDecimal("0.05000"));
        salesItem.setForSales(true);
        salesItem.setForPurchases(true);
        salesItem.setAgency(agency);
        salesItem.setSalesReturnLine("103");
        TaxGroup salesGroup = new TaxGroup();
        salesGroup.setTaxItems(List.of(salesItem));
        TaxCode taxCode = new TaxCode();
        taxCode.setCode("FED");
        taxCode.setSalesTaxGroup(salesGroup);
        when(taxCodeService.getItemsByAgency(10L)).thenReturn(List.of(salesItem));
        when(taxCodeService.getAllCodes()).thenReturn(List.of(taxCode));

        Invoice invoice = new Invoice();
        invoice.setTaxRegime("FED");
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setGstAmount(new BigDecimal("5.00"));
        invoice.setHstAmount(BigDecimal.ZERO);
        invoice.setQstAmount(BigDecimal.ZERO);
        when(invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                eq(1L), eq(start), eq(end))).thenReturn(List.of(invoice));
        when(journalService.getJournalsByDateRange(start, end)).thenReturn(List.of());

        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setAgency(agency);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);

        List<TaxReturnLineDTO> breakdown = service.getReturnLineBreakdown(period);
        List<TaxReturnRowDTO> rows = service.getTaxReturnRows(period, breakdown);

        assertEquals(new BigDecimal("100.00"), findRow(rows, "101").amount());
        assertEquals(new BigDecimal("5.00"), findRow(rows, "105").balance());
        assertEquals(BigDecimal.ZERO, findRow(rows, "108").balance());
        assertEquals(new BigDecimal("5.00"), findRow(rows, "116").balance());
        assertEquals(new BigDecimal("5.00"), rows.get(rows.size() - 1).balance());
    }

    @Test
    void includesOtherIncomeJournalEntriesWithoutDoubleCountingInvoiceJournals() {
        Invoice invoice = new Invoice();
        invoice.setSubtotal(new BigDecimal("100.00"));
        when(invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                eq(1L), eq(start), eq(end))).thenReturn(List.of(invoice));

        ChartOfAccount otherIncome = new ChartOfAccount();
        otherIncome.setAccountType(AccountType.REVENUE);
        otherIncome.setCategory(AccountCategory.OTHER_INCOME);
        GeneralJournal manualJournal = revenueJournal("OTHER-INCOME-1", otherIncome, "75.00");

        ChartOfAccount income = new ChartOfAccount();
        income.setAccountType(AccountType.REVENUE);
        income.setCategory(AccountCategory.INCOME);
        GeneralJournal manualSalesJournal = revenueJournal("MANUAL-SALE-1", income, "50.00");

        GeneralJournal invoiceJournal = revenueJournal("INVOICE-123", income, "100.00");
        when(journalService.isDirectlyEntered(manualJournal)).thenReturn(true);
        when(journalService.isDirectlyEntered(manualSalesJournal)).thenReturn(true);
        when(journalService.getJournalsByDateRange(start, end))
                .thenReturn(List.of(manualJournal, manualSalesJournal, invoiceJournal));

        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setAgency(agency);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);

        List<TaxReturnRowDTO> rows = service.getTaxReturnRows(period, List.of());

        assertEquals(new BigDecimal("225.00"), findRow(rows, "101").amount());
    }

    @Test
    void calculatesCombinedSalesTaxAndQstReturnTotals() {
        agency.setCode(TaxFilingService.MRQ2013_CODE);
        Invoice invoice = new Invoice();
        invoice.setSubtotal(new BigDecimal("33475.00"));
        when(invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                eq(1L), eq(start), eq(end))).thenReturn(List.of(invoice));
        when(journalService.getJournalsByDateRange(start, end)).thenReturn(List.of());

        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setAgency(agency);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);
        List<TaxReturnLineDTO> breakdown = List.of(
                new TaxReturnLineDTO("SALES", "103", new BigDecimal("1709.80")),
                new TaxReturnLineDTO("PURCHASES", "106", new BigDecimal("18.00")),
                new TaxReturnLineDTO("SALES", "203", new BigDecimal("3411.06")));

        List<TaxReturnRowDTO> rows = service.getTaxReturnRows(period, breakdown);

        assertEquals(new BigDecimal("33475.00"), findRow(rows, "101").amount());
        assertEquals(new BigDecimal("1691.80"), findRow(rows, "109").balance());
        assertEquals(new BigDecimal("3411.06"), findRow(rows, "213").balance());
        assertEquals(new BigDecimal("5102.86"), rows.get(rows.size() - 1).balance());
    }

    @Test
    void usesAgencyTaxItemsWhenARegimeGroupContainsItemsForAnotherAgency() {
        TaxAgency cra = new TaxAgency();
        cra.setId(11L);
        cra.setCode(TaxFilingService.CRA_CODE);
        TaxItem craItem = new TaxItem();
        craItem.setCode("TPS");
        craItem.setRate(new BigDecimal("0.05000"));
        craItem.setForSales(true);
        craItem.setForPurchases(true);
        craItem.setAgency(cra);
        TaxGroup salesGroup = new TaxGroup();
        salesGroup.setTaxItems(List.of(craItem));
        TaxCode taxCode = new TaxCode();
        taxCode.setCode("QC");
        taxCode.setSalesTaxGroup(salesGroup);

        agency.setCode(TaxFilingService.MRQ2013_CODE);
        TaxItem legacyItem = new TaxItem();
        legacyItem.setCode("S13-TPS");
        legacyItem.setRate(new BigDecimal("0.05000"));
        legacyItem.setForSales(true);
        legacyItem.setForPurchases(true);
        legacyItem.setAgency(agency);
        legacyItem.setSalesReturnLine("103");
        when(taxCodeService.getItemsByAgency(10L)).thenReturn(List.of(legacyItem));
        when(taxCodeService.getAllCodes()).thenReturn(List.of(taxCode));

        Invoice invoice = new Invoice();
        invoice.setTaxRegime("QC");
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setGstAmount(new BigDecimal("5.00"));
        invoice.setHstAmount(BigDecimal.ZERO);
        invoice.setQstAmount(BigDecimal.ZERO);
        when(invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                eq(1L), eq(start), eq(end))).thenReturn(List.of(invoice));
        when(journalService.getJournalsByDateRange(start, end)).thenReturn(List.of());

        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setAgency(agency);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);

        assertEquals(List.of(new TaxReturnLineDTO("SALES", "103", new BigDecimal("5.00"))),
                service.getReturnLineBreakdown(period));
    }

    @Test
    void reportsUnmappedInvoiceTaxesInsteadOfOmittingThem() {
        TaxItem salesItem = new TaxItem();
        salesItem.setId(32L);
        salesItem.setCode("GST");
        salesItem.setRate(new BigDecimal("0.05000"));
        salesItem.setForSales(true);
        salesItem.setForPurchases(true);
        salesItem.setAgency(agency);
        TaxGroup salesGroup = new TaxGroup();
        salesGroup.setTaxItems(List.of(salesItem));
        TaxCode taxCode = new TaxCode();
        taxCode.setCode("FED");
        taxCode.setSalesTaxGroup(salesGroup);
        when(taxCodeService.getItemsByAgency(10L)).thenReturn(List.of(salesItem));
        when(taxCodeService.getAllCodes()).thenReturn(List.of(taxCode));

        Invoice invoice = new Invoice();
        invoice.setTaxRegime("FED");
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setGstAmount(new BigDecimal("5.00"));
        invoice.setHstAmount(BigDecimal.ZERO);
        invoice.setQstAmount(BigDecimal.ZERO);
        when(invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                eq(1L), eq(start), eq(end))).thenReturn(List.of(invoice));
        when(journalService.getJournalsByDateRange(start, end)).thenReturn(List.of());

        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setAgency(agency);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);

        assertEquals(List.of(new TaxReturnLineDTO("SALES", null, new BigDecimal("5.00"))),
                service.getReturnLineBreakdown(period));
        org.junit.jupiter.api.Assertions.assertTrue(service.hasUnmappedReturnLineAmounts(period));
    }

    private TaxReturnRowDTO findRow(List<TaxReturnRowDTO> rows, String line) {
        return rows.stream()
                .filter(row -> java.util.Objects.equals(line, row.line()))
                .findFirst()
                .orElseThrow();
    }

    private GeneralJournal revenueJournal(String reference, ChartOfAccount account, String amount) {
        GeneralJournal journal = new GeneralJournal();
        journal.setReference(reference);
        journal.setStatus(JournalStatus.POSTED);
        journal.setJournalDate(start);
        JournalEntry entry = new JournalEntry();
        entry.setJournal(journal);
        entry.setAccount(account);
        entry.setDebit(BigDecimal.ZERO);
        entry.setCredit(new BigDecimal(amount));
        journal.setEntries(List.of(entry));
        return journal;
    }

    private GeneralJournal journal(JournalStatus status, BigDecimal debit, BigDecimal credit,
                                   boolean onItcAccount) {
        GeneralJournal journal = new GeneralJournal();
        journal.setStatus(status);
        journal.setJournalDate(start);
        JournalEntry entry = new JournalEntry();
        entry.setJournal(journal);
        ChartOfAccount account = onItcAccount ? itcAccount : new ChartOfAccount();
        if (!onItcAccount) {
            account.setAccountType(AccountType.EXPENSE);
        }
        entry.setAccount(account);
        entry.setDebit(debit);
        entry.setCredit(credit);
        entry.setTaxItem(purchaseItem);
        Vendor vendor = new Vendor();
        entry.setVendor(vendor);
        journal.setEntries(List.of(entry));
        return journal;
    }

    private CurrentCompanyContext mockCompanyContext() {
        CurrentCompanyContext context = mock(CurrentCompanyContext.class);
        when(context.requireCompanyId()).thenReturn(1L);
        return context;
    }
}
