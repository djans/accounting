package com.cogitosum.web;

import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.entity.Payment;
import com.cogitosum.service.CustomerService;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.service.GeneralJournalService;
import com.cogitosum.service.PaymentService;
import com.cogitosum.service.TaxAgencyService;
import com.cogitosum.service.TaxCodeService;
import com.cogitosum.service.VendorService;
import com.cogitosum.service.TaxFilingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/journals")
public class JournalWebController {
    private static final Logger log = LoggerFactory.getLogger(JournalWebController.class);

    @Autowired private GeneralJournalService journalService;
    @Autowired private ChartOfAccountService accountService;
    @Autowired private PaymentService paymentService;
    @Autowired private MessageSource messageSource;
    @Autowired private CustomerService customerService;
    @Autowired private VendorService vendorService;
    @Autowired private TaxAgencyService taxAgencyService;
    @Autowired private TaxCodeService taxCodeService;
    @Autowired private TaxFilingService taxFilingService;

    @GetMapping
    public String list(@RequestParam(required = false) JournalStatus status, Model model) {
        List<GeneralJournal> journals = status == null
                ? journalService.getAllJournals()
                : journalService.getJournalsByStatus(status);
        Map<Long, BigDecimal> journalAmounts = new HashMap<>();
        for (GeneralJournal journal : journals) {
            BigDecimal amount = journal.getEntries().stream()
                    .map(entry -> entry.getDebit() == null ? BigDecimal.ZERO : entry.getDebit())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            journalAmounts.put(journal.getId(), amount);
        }
        model.addAttribute("journals", journals);
        model.addAttribute("journalAmounts", journalAmounts);
        model.addAttribute("journalStatuses", JournalStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("dateFilterPeriods", taxFilingService.getDateFilterPeriods(LocalDate.now()));
        return "journals/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        addFormOptions(model);
        model.addAttribute("pageTitle", message("nav.makeJournalEntries"));
        model.addAttribute("journalDate", LocalDate.now());
        model.addAttribute("narrative", "");
        model.addAttribute("reference", "");
        model.addAttribute("entryRows", List.of(blankEntryRow(), blankEntryRow()));
        model.addAttribute("editing", false);
        model.addAttribute("postedEditing", false);
        model.addAttribute("copying", false);
        return "journals/form";
    }

    @GetMapping("/{id}/edit")
    public String editDraft(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            GeneralJournal journal = journalService.getJournalForEdit(id);
            boolean postedEditing = journal.getStatus() == JournalStatus.POSTED;
            addFormOptions(model, true);
            model.addAttribute("journal", journal);
            model.addAttribute("pageTitle", message(postedEditing ? "journal.editPosted" : "journal.editDraft"));
            model.addAttribute("journalDate", journal.getJournalDate());
            model.addAttribute("narrative", journal.getNarrative());
            model.addAttribute("reference", journal.getReference());
            model.addAttribute("entryRows", journal.getEntries().stream()
                    .map(this::toFormRow)
                    .toList());
            model.addAttribute("editing", true);
            model.addAttribute("postedEditing", postedEditing);
            return "journals/form";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("journal.editError", e.getMessage()));
            return "redirect:/journals";
        }
    }

    @GetMapping("/{id}/copy")
    public String copyDirectJournal(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            GeneralJournal journal = journalService.getDirectJournalForCopy(id);
            addFormOptions(model, true);
            model.addAttribute("pageTitle", message("journal.copyDraft"));
            model.addAttribute("journalDate", LocalDate.now());
            model.addAttribute("narrative", journal.getNarrative());
            model.addAttribute("reference", journal.getReference());
            model.addAttribute("entryRows", journal.getEntries().stream()
                    .map(this::toFormRow)
                    .toList());
            model.addAttribute("editing", false);
            model.addAttribute("postedEditing", false);
            model.addAttribute("copying", true);
            return "journals/form";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("journal.copyError", e.getMessage()));
            return "redirect:/journals/" + id;
        }
    }

    @PostMapping
    public String create(@RequestParam String narrative,
                         @RequestParam(required = false) String reference,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journalDate,
                         @RequestParam List<Long> accountIds,
                         @RequestParam List<BigDecimal> debits,
                         @RequestParam List<BigDecimal> credits,
                         @RequestParam(required = false) List<String> partyRefs,
                         @RequestParam(required = false) List<Long> taxItemIds,
                         @RequestParam(required = false) List<String> descriptions,
                         RedirectAttributes ra) {
        try {
            GeneralJournal journal = buildJournal(
                    narrative, reference, journalDate, accountIds, debits, credits,
                    partyRefs, taxItemIds, descriptions);
            GeneralJournal saved = journalService.createJournal(journal);
            ra.addFlashAttribute("flashSuccess", "Journal " + saved.getJournalNumber() + " created as DRAFT");
            return "redirect:/journals/" + saved.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", "Could not create journal: " + e.getMessage());
            return "redirect:/journals/new";
        }
    }

    @PostMapping("/{id}")
    public String updateDraft(@PathVariable Long id,
                              @RequestParam String narrative,
                              @RequestParam(required = false) String reference,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journalDate,
                              @RequestParam List<Long> accountIds,
                              @RequestParam List<BigDecimal> debits,
                              @RequestParam List<BigDecimal> credits,
                              @RequestParam(required = false) List<String> partyRefs,
                              @RequestParam(required = false) List<Long> taxItemIds,
                              @RequestParam(required = false) List<String> descriptions,
                              Authentication authentication,
                              RedirectAttributes ra) {
        try {
            GeneralJournal journal = buildJournal(
                    narrative, reference, journalDate, accountIds, debits, credits,
                    partyRefs, taxItemIds, descriptions);
            GeneralJournal updated = journalService.updateJournal(id, journal);
            if (updated == null) {
                throw new IllegalArgumentException("Journal not found");
            }
            if (updated.getStatus() == JournalStatus.POSTED) {
                log.warn("Posted journal edited: journal={} actor={} reference={} date={} lines={}",
                        id, authentication.getName(), updated.getReference(), updated.getJournalDate(),
                        updated.getEntries().size());
                ra.addFlashAttribute("flashSuccess", message("journal.postedUpdated"));
            } else {
                ra.addFlashAttribute("flashSuccess", message("journal.updated"));
            }
            return "redirect:/journals/" + id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("journal.saveError", e.getMessage()));
            return "redirect:/journals/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/posted-date")
    public String updatePostedDate(@PathVariable Long id,
                                   @RequestParam
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journalDate,
                                   Authentication authentication,
                                   RedirectAttributes ra) {
        try {
            GeneralJournalService.PostedJournalDateChange change =
                    journalService.updatePostedJournalDate(id, journalDate);
            log.warn("Posted journal date changed: journal={} actor={} oldDate={} newDate={}",
                    id, authentication.getName(), change.previousDate(), change.newDate());
            ra.addFlashAttribute("flashSuccess", message(
                    "journal.postedDateUpdated", change.previousDate(), change.newDate()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("journal.postedDateError", e.getMessage()));
        }
        return "redirect:/journals/" + id;
    }

    private GeneralJournal buildJournal(String narrative, String reference, LocalDate journalDate,
                                        List<Long> accountIds, List<BigDecimal> debits,
                                        List<BigDecimal> credits, List<String> partyRefs,
                                        List<Long> taxItemIds, List<String> descriptions) {
        GeneralJournal journal = new GeneralJournal();
        journal.setNarrative(narrative);
        journal.setReference(reference);
        journal.setJournalDate(journalDate);

        List<JournalEntry> entries = new ArrayList<>();
        for (int i = 0; i < accountIds.size(); i++) {
            Long accId = accountIds.get(i);
            if (accId == null) continue;
            ChartOfAccount account = accountService.getAccountById(accId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accId));
            BigDecimal debit = valueAt(debits, i) != null ? valueAt(debits, i) : BigDecimal.ZERO;
            BigDecimal credit = valueAt(credits, i) != null ? valueAt(credits, i) : BigDecimal.ZERO;
            if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            JournalEntry entry = new JournalEntry();
            entry.setAccount(account);
            entry.setDebit(debit);
            entry.setCredit(credit);
            entry.setLineNumber(entries.size() + 1);
            applyPartyReference(entry, valueAt(partyRefs, i));
            Long taxItemId = valueAt(taxItemIds, i);
            if (taxItemId != null) {
                entry.setTaxItem(taxCodeService.getItemById(taxItemId)
                        .orElseThrow(() -> new IllegalArgumentException("Tax item not found: " + taxItemId)));
            }
            entry.setDescription(valueAt(descriptions, i));
            entries.add(entry);
        }
        if (entries.size() < 2) {
            throw new IllegalArgumentException("A journal needs at least two entries with amounts");
        }
        journal.setEntries(entries);
        return journal;
    }

    private JournalEntryFormRow toFormRow(JournalEntry entry) {
        String partyRef = entry.getCustomer() != null ? "CUSTOMER:" + entry.getCustomer().getId()
                : entry.getVendor() != null ? "VENDOR:" + entry.getVendor().getId()
                : entry.getTaxAgency() != null ? "TAX_AGENCY:" + entry.getTaxAgency().getId()
                : "";
        return new JournalEntryFormRow(
                entry.getAccount().getId(),
                entry.getDebit() != null ? entry.getDebit() : BigDecimal.ZERO,
                entry.getCredit() != null ? entry.getCredit() : BigDecimal.ZERO,
                partyRef,
                entry.getTaxItem() != null ? entry.getTaxItem().getId() : null,
                entry.getDescription());
    }

    private JournalEntryFormRow blankEntryRow() {
        return new JournalEntryFormRow(null, BigDecimal.ZERO, BigDecimal.ZERO, "", null, "");
    }

    public record JournalEntryFormRow(Long accountId, BigDecimal debit, BigDecimal credit,
                                      String partyRef, Long taxItemId, String description) {
    }

    private void addFormOptions(Model model) {
        addFormOptions(model, false);
    }

    private void addFormOptions(Model model, boolean includeInactive) {
        model.addAttribute("accounts",
                includeInactive ? accountService.getAllAccounts() : accountService.getActiveAccounts());
        model.addAttribute("customers", customerService.getAllCustomers().stream()
                .filter(customer -> includeInactive || !customer.isInactive())
                .toList());
        model.addAttribute("vendors", vendorService.getAllVendors().stream()
                .filter(vendor -> includeInactive || !vendor.isInactive())
                .toList());
        model.addAttribute("taxAgencies",
                includeInactive ? taxAgencyService.getAll() : taxAgencyService.getActiveAgencies());
        model.addAttribute("taxItems",
                includeInactive ? taxCodeService.getAllItems() : taxCodeService.getActiveItems());
    }

    private void applyPartyReference(JournalEntry entry, String partyRef) {
        if (partyRef == null || partyRef.isBlank()) {
            return;
        }

        String[] parts = partyRef.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid NAME selection");
        }
        Long id = Long.valueOf(parts[1]);
        switch (parts[0]) {
            case "CUSTOMER" -> entry.setCustomer(customerService.getCustomerById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id)));
            case "VENDOR" -> entry.setVendor(vendorService.getVendorById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + id)));
            case "TAX_AGENCY" -> entry.setTaxAgency(taxAgencyService.getById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Tax agency not found: " + id)));
            default -> throw new IllegalArgumentException("Invalid NAME selection");
        }
    }

    private static <T> T valueAt(List<T> values, int index) {
        return values != null && index < values.size() ? values.get(index) : null;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return journalService.getJournalById(id)
                .map(j -> {
                    BigDecimal totalDr = j.getEntries().stream()
                            .map(e -> e.getDebit() == null ? BigDecimal.ZERO : e.getDebit())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalCr = j.getEntries().stream()
                            .map(e -> e.getCredit() == null ? BigDecimal.ZERO : e.getCredit())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    model.addAttribute("journal", j);
                    model.addAttribute("totalDr", totalDr);
                    model.addAttribute("totalCr", totalCr);
                    model.addAttribute("postedJournalEditingEnabled",
                            journalService.isPostedJournalEditingEnabled());
                    model.addAttribute("directJournal", journalService.isDirectlyEntered(j));
                    return "journals/detail";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("flashError", "Journal not found");
                    return "redirect:/journals";
                });
    }

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id,
                       @RequestParam(defaultValue = "portal") String postedBy,
                       RedirectAttributes ra) {
        try {
            journalService.postJournal(id, postedBy);
            ra.addFlashAttribute("flashSuccess", "Journal posted to the General Ledger");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not post journal: " + e.getMessage());
        }
        return "redirect:/journals/" + id;
    }

    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable Long id,
                          @RequestParam String reason,
                          RedirectAttributes ra) {
        try {
            Optional<GeneralJournal> journal = journalService.getJournalById(id);
            if (journal.isPresent() && isPaymentReference(journal.get().getReference())) {
                Long paymentId = Long.parseLong(journal.get().getReference().substring("PAYMENT-".length()));
                Payment payment = paymentService.refundPayment(paymentId, reason);
                ra.addFlashAttribute("flashSuccess",
                        message("payments.reverse.success", payment.getTransactionId()));
                return "redirect:/invoices/" + payment.getInvoice().getId();
            }
            if (journal.isPresent() && isPaymentReversal(journal.get())) {
                throw new IllegalStateException(message("payments.reverse.reversalBlocked"));
            }

            GeneralJournal reversal = journalService.reverseJournal(id, reason);
            ra.addFlashAttribute("flashSuccess",
                    "Reversal posted as " + (reversal != null ? reversal.getJournalNumber() : ""));
            return "redirect:/journals/" + (reversal != null ? reversal.getId() : id);
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not reverse: " + e.getMessage());
            return "redirect:/journals/" + id;
        }
    }

    private boolean isPaymentReference(String reference) {
        return reference != null && reference.matches("PAYMENT-\\d+");
    }

    private boolean isPaymentReversal(GeneralJournal journal) {
        String reference = journal.getReference();
        String prefix = "Reversal of ";
        if (reference == null || !reference.startsWith(prefix)) {
            return false;
        }
        return journalService.getJournalByNumber(reference.substring(prefix.length()))
                .map(original -> isPaymentReference(original.getReference()))
                .orElse(false);
    }

    private String message(String code, Object... arguments) {
        return messageSource.getMessage(code, arguments, LocaleContextHolder.getLocale());
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            journalService.deleteJournal(id);
            ra.addFlashAttribute("flashSuccess", "Journal deleted");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not delete: " + e.getMessage());
        }
        return "redirect:/journals";
    }
}
