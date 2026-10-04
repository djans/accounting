package com.cogitosum.web;

import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.entity.Payment;
import com.cogitosum.entity.TaxItem;
import com.cogitosum.service.CustomerService;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.service.GeneralJournalService;
import com.cogitosum.service.PaymentService;
import com.cogitosum.service.TaxAgencyService;
import com.cogitosum.service.TaxCodeService;
import com.cogitosum.service.VendorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JournalWebControllerTest {

    private GeneralJournalService journalService;
    private PaymentService paymentService;
    private ChartOfAccountService accountService;
    private CustomerService customerService;
    private VendorService vendorService;
    private TaxAgencyService taxAgencyService;
    private TaxCodeService taxCodeService;
    private StaticMessageSource messages;
    private JournalWebController controller;

    @BeforeEach
    void setUp() {
        journalService = mock(GeneralJournalService.class);
        paymentService = mock(PaymentService.class);
        messages = new StaticMessageSource();
        messages.addMessage("payments.reverse.success", Locale.ENGLISH, "Payment {0} reversed");
        messages.addMessage("payments.reverse.reversalBlocked", Locale.ENGLISH,
                "A payment reversal cannot be reversed directly");
        messages.addMessage("journal.updated", Locale.ENGLISH, "Draft updated");
        messages.addMessage("journal.saveError", Locale.ENGLISH, "Could not update journal: {0}");
        messages.addMessage("journal.postedUpdated", Locale.ENGLISH, "Posted journal updated");
        messages.addMessage("journal.editDraft", Locale.ENGLISH, "Edit draft journal");
        messages.addMessage("journal.editPosted", Locale.ENGLISH, "Edit posted journal");
        messages.addMessage("journal.copyDraft", Locale.ENGLISH, "Copy to a new draft");
        messages.addMessage("nav.makeJournalEntries", Locale.ENGLISH, "Make Journal Entries");
        messages.addMessage("journal.postedDateUpdated", Locale.ENGLISH,
                "Date changed from {0} to {1}");
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        controller = new JournalWebController();
        ReflectionTestUtils.setField(controller, "journalService", journalService);
        ReflectionTestUtils.setField(controller, "paymentService", paymentService);
        accountService = mock(ChartOfAccountService.class);
        customerService = mock(CustomerService.class);
        vendorService = mock(VendorService.class);
        taxAgencyService = mock(TaxAgencyService.class);
        taxCodeService = mock(TaxCodeService.class);
        ReflectionTestUtils.setField(controller, "accountService", accountService);
        ReflectionTestUtils.setField(controller, "messageSource", messages);
        ReflectionTestUtils.setField(controller, "customerService", customerService);
        ReflectionTestUtils.setField(controller, "vendorService", vendorService);
        ReflectionTestUtils.setField(controller, "taxAgencyService", taxAgencyService);
        ReflectionTestUtils.setField(controller, "taxCodeService", taxCodeService);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void listsAllJournalStatusesWhenNoFilterIsSelected() {
        when(journalService.getAllJournals()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.list(null, model);

        assertEquals("journals/list", view);
        assertEquals(JournalStatus.values().length, ((JournalStatus[]) model.get("journalStatuses")).length);
        assertNull(model.get("selectedStatus"));
        verify(journalService).getAllJournals();
        verify(journalService, never()).getJournalsByStatus(any());
    }

    @Test
    void filtersJournalsBySelectedStatus() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.list(JournalStatus.REVERSED, model);

        assertEquals("journals/list", view);
        assertEquals(JournalStatus.REVERSED, model.get("selectedStatus"));
        verify(journalService).getJournalsByStatus(JournalStatus.REVERSED);
        verify(journalService, never()).getAllJournals();
    }

    @Test
    void journalListShowsTheBalancedTotalDebitAsAmount() {
        GeneralJournal journal = new GeneralJournal();
        journal.setId(41L);
        JournalEntry debit = new JournalEntry();
        debit.setDebit(new BigDecimal("75.00"));
        JournalEntry credit = new JournalEntry();
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(new BigDecimal("75.00"));
        journal.getEntries().addAll(List.of(debit, credit));
        when(journalService.getAllJournals()).thenReturn(List.of(journal));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.list(null, model);

        assertEquals(new BigDecimal("75.00"),
                ((java.util.Map<?, ?>) model.get("journalAmounts")).get(41L));
    }

    @Test
    void newJournalFormDefaultsDateToToday() {
        when(accountService.getActiveAccounts()).thenReturn(List.of());
        when(customerService.getAllCustomers()).thenReturn(List.of());
        when(vendorService.getAllVendors()).thenReturn(List.of());
        when(taxAgencyService.getActiveAgencies()).thenReturn(List.of());
        when(taxCodeService.getActiveItems()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.newForm(model);

        assertEquals("journals/form", view);
        assertEquals(LocalDate.now(), model.get("journalDate"));
    }

    @Test
    void editDraftFormRestoresJournalHeaderAndEntryValues() {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(101L);
        GeneralJournal draft = new GeneralJournal();
        draft.setId(401L);
        draft.setJournalDate(LocalDate.of(2026, 10, 1));
        draft.setNarrative("Office purchase");
        draft.setReference("INV-123");
        draft.setStatus(JournalStatus.DRAFT);
        JournalEntry entry = new JournalEntry();
        entry.setAccount(account);
        entry.setDebit(new BigDecimal("25.00"));
        entry.setCredit(BigDecimal.ZERO);
        entry.setDescription("Supplies");
        draft.getEntries().add(entry);
        when(journalService.getJournalForEdit(401L)).thenReturn(draft);
        when(accountService.getAllAccounts()).thenReturn(List.of(account));
        when(customerService.getAllCustomers()).thenReturn(List.of());
        when(vendorService.getAllVendors()).thenReturn(List.of());
        when(taxAgencyService.getAll()).thenReturn(List.of());
        when(taxCodeService.getAllItems()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.editDraft(401L, model, new RedirectAttributesModelMap());

        assertEquals("journals/form", view);
        assertEquals(true, model.get("editing"));
        assertEquals(LocalDate.of(2026, 10, 1), model.get("journalDate"));
        assertEquals("Office purchase", model.get("narrative"));
        assertEquals("INV-123", model.get("reference"));
        assertEquals(new JournalWebController.JournalEntryFormRow(
                101L, new BigDecimal("25.00"), BigDecimal.ZERO, "", null, "Supplies"),
                ((List<?>) model.get("entryRows")).get(0));
    }

    @Test
    void copyingDirectJournalOpensANewPrefilledDraft() {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(101L);
        GeneralJournal source = new GeneralJournal();
        source.setId(401L);
        source.setJournalDate(LocalDate.of(2026, 9, 29));
        source.setNarrative("Office purchase");
        source.setReference("INV-123");
        JournalEntry entry = new JournalEntry();
        entry.setAccount(account);
        entry.setDebit(new BigDecimal("25.00"));
        entry.setCredit(BigDecimal.ZERO);
        entry.setDescription("Supplies");
        source.getEntries().add(entry);
        when(journalService.getDirectJournalForCopy(401L)).thenReturn(source);
        when(accountService.getAllAccounts()).thenReturn(List.of(account));
        when(customerService.getAllCustomers()).thenReturn(List.of());
        when(vendorService.getAllVendors()).thenReturn(List.of());
        when(taxAgencyService.getAll()).thenReturn(List.of());
        when(taxCodeService.getAllItems()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.copyDirectJournal(401L, model, new RedirectAttributesModelMap());

        assertEquals("journals/form", view);
        assertEquals(false, model.get("editing"));
        assertEquals(true, model.get("copying"));
        assertEquals(LocalDate.now(), model.get("journalDate"));
        assertEquals("Office purchase", model.get("narrative"));
        assertEquals("INV-123", model.get("reference"));
        assertEquals(new JournalWebController.JournalEntryFormRow(
                101L, new BigDecimal("25.00"), BigDecimal.ZERO, "", null, "Supplies"),
                ((List<?>) model.get("entryRows")).get(0));
    }

    @Test
    void updatingDraftPassesTheSelectedDateToJournalService() {
        ChartOfAccount debitAccount = new ChartOfAccount();
        debitAccount.setId(101L);
        ChartOfAccount creditAccount = new ChartOfAccount();
        creditAccount.setId(102L);
        when(accountService.getAccountById(101L)).thenReturn(Optional.of(debitAccount));
        when(accountService.getAccountById(102L)).thenReturn(Optional.of(creditAccount));
        when(journalService.updateJournal(eq(401L), any(GeneralJournal.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin@example.test");

        String view = controller.updateDraft(
                401L, "Purchase", "INV-123", LocalDate.of(2026, 9, 30),
                List.of(101L, 102L),
                List.of(new BigDecimal("100.00"), BigDecimal.ZERO),
                List.of(BigDecimal.ZERO, new BigDecimal("100.00")),
                null, null, List.of("Expense", "Card"),
                authentication,
                attributes);

        assertEquals("redirect:/journals/401", view);
        assertEquals("Draft updated", attributes.getFlashAttributes().get("flashSuccess"));
        org.mockito.ArgumentCaptor<GeneralJournal> journalCaptor =
                org.mockito.ArgumentCaptor.forClass(GeneralJournal.class);
        verify(journalService).updateJournal(eq(401L), journalCaptor.capture());
        assertEquals(LocalDate.of(2026, 9, 30), journalCaptor.getValue().getJournalDate());
    }

    @Test
    void postedDateActionCallsDateOnlyServiceAndReportsChange() {
        LocalDate previousDate = LocalDate.of(2026, 9, 29);
        LocalDate newDate = LocalDate.of(2026, 9, 30);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin@example.test");
        when(journalService.updatePostedJournalDate(401L, newDate)).thenReturn(
                new com.cogitosum.service.GeneralJournalService.PostedJournalDateChange(previousDate, newDate));
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.updatePostedDate(401L, newDate, authentication, attributes);

        assertEquals("redirect:/journals/401", view);
        assertEquals("Date changed from 2026-09-29 to 2026-09-30",
                attributes.getFlashAttributes().get("flashSuccess"));
        verify(journalService).updatePostedJournalDate(401L, newDate);
    }

    @Test
    void reversingPaymentJournalUsesPaymentLifecycleAndReturnsToInvoice() {
        GeneralJournal journal = journal("PAYMENT-7");
        Invoice invoice = new Invoice();
        invoice.setId(10L);
        Payment payment = new Payment();
        payment.setTransactionId("TXN-7");
        payment.setInvoice(invoice);
        when(journalService.getJournalById(33L)).thenReturn(Optional.of(journal));
        when(paymentService.refundPayment(7L, "Duplicate receipt")).thenReturn(payment);

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();
        String view = controller.reverse(33L, "Duplicate receipt", attributes);

        assertEquals("redirect:/invoices/10", view);
        assertEquals("Payment TXN-7 reversed", attributes.getFlashAttributes().get("flashSuccess"));
        verify(paymentService).refundPayment(7L, "Duplicate receipt");
        verify(journalService, never()).reverseJournal(anyLong(), anyString());
    }

    @Test
    void paymentReversalJournalCannotBeReversedIndependently() {
        when(journalService.getJournalById(34L)).thenReturn(Optional.of(journal("Reversal of JL-7")));
        when(journalService.getJournalByNumber("JL-7")).thenReturn(Optional.of(journal("PAYMENT-7")));

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();
        String view = controller.reverse(34L, "Undo reversal", attributes);

        assertEquals("redirect:/journals/34", view);
        assertEquals("Could not reverse: A payment reversal cannot be reversed directly",
                attributes.getFlashAttributes().get("flashError"));
        verifyNoInteractions(paymentService);
        verify(journalService, never()).reverseJournal(anyLong(), anyString());
    }

    @Test
    void createsJournalLinesWithSelectedNameAndTaxItem() {
        ChartOfAccount expense = new ChartOfAccount();
        expense.setId(101L);
        ChartOfAccount bank = new ChartOfAccount();
        bank.setId(102L);
        when(accountService.getAccountById(101L)).thenReturn(Optional.of(expense));
        when(accountService.getAccountById(102L)).thenReturn(Optional.of(bank));

        Customer customer = new Customer();
        customer.setId(201L);
        when(customerService.getCustomerById(201L)).thenReturn(Optional.of(customer));
        TaxItem taxItem = new TaxItem();
        taxItem.setId(301L);
        when(taxCodeService.getItemById(301L)).thenReturn(Optional.of(taxItem));
        when(journalService.createJournal(any(GeneralJournal.class))).thenAnswer(invocation -> {
            GeneralJournal journal = invocation.getArgument(0);
            journal.setId(401L);
            journal.setJournalNumber("JL-401");
            return journal;
        });

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();
        String view = controller.create(
                "Purchase", null, LocalDate.of(2026, 10, 2),
                List.of(101L, 102L),
                List.of(new BigDecimal("100.00"), BigDecimal.ZERO),
                List.of(BigDecimal.ZERO, new BigDecimal("100.00")),
                List.of("CUSTOMER:201", ""),
                java.util.Arrays.asList(301L, null),
                List.of("Office supplies", "Payment"),
                attributes);

        assertEquals("redirect:/journals/401", view);
        org.mockito.ArgumentCaptor<GeneralJournal> journalCaptor =
                org.mockito.ArgumentCaptor.forClass(GeneralJournal.class);
        verify(journalService).createJournal(journalCaptor.capture());
        assertEquals(LocalDate.of(2026, 10, 2), journalCaptor.getValue().getJournalDate());
        JournalEntry firstEntry = journalCaptor.getValue().getEntries().get(0);
        assertEquals(customer, firstEntry.getCustomer());
        assertEquals(taxItem, firstEntry.getTaxItem());
        assertNull(firstEntry.getVendor());
        assertNull(firstEntry.getTaxAgency());
        assertNull(journalCaptor.getValue().getEntries().get(1).getTaxItem());
    }

    private GeneralJournal journal(String reference) {
        GeneralJournal journal = new GeneralJournal();
        journal.setReference(reference);
        return journal;
    }
}
