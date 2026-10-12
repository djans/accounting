package com.cogitosum.web;

import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteStatus;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.PaymentMethod;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.service.CreditNoteService;
import com.cogitosum.service.CreditNoteRefundService;
import com.cogitosum.service.CreditNoteAllocationService;
import com.cogitosum.service.InvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/credit-notes")
public class CreditNoteWebController {

    private final CreditNoteService creditNoteService;
    private final InvoiceService invoiceService;
    @Autowired private CreditNoteRefundService refundService;
    @Autowired private CreditNoteAllocationService allocationService;
    @Autowired private ChartOfAccountService accountService;
    @Autowired private MessageSource messageSource;

    public CreditNoteWebController(CreditNoteService creditNoteService, InvoiceService invoiceService) {
        this.creditNoteService = creditNoteService;
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("creditNotes", creditNoteService.getAllCreditNotes());
        return "credit-notes/list";
    }

    @GetMapping("/from-invoice/{invoiceId}/new")
    public String newFromInvoice(@PathVariable Long invoiceId, Model model, RedirectAttributes redirect) {
        Invoice invoice = invoiceService.getInvoiceById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (!creditNoteService.canCredit(invoice)
                || creditNoteService.getRemainingCreditableAmount(invoice).signum() <= 0) {
            redirect.addFlashAttribute("flashError", "This invoice cannot receive another credit note");
            return "redirect:/invoices/" + invoiceId;
        }
        model.addAttribute("invoice", invoice);
        model.addAttribute("remainingCreditableAmount", creditNoteService.getRemainingCreditableAmount(invoice));
        model.addAttribute("creditNoteDate", LocalDate.now());
        model.addAttribute("reason", "");
        model.addAttribute("lines", invoice.getLineItems().stream()
                .map(line -> new CreditNoteService.LineInput(
                        line.getDescription(), line.getQuantity(), line.getUnitPrice()))
                .toList());
        model.addAttribute("isEdit", false);
        return "credit-notes/form";
    }

    @PostMapping("/from-invoice/{invoiceId}")
    public String create(@PathVariable Long invoiceId,
                         @RequestParam String reason,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate creditNoteDate,
                         @RequestParam(required = false) List<String> descriptions,
                         @RequestParam(required = false) List<BigDecimal> quantities,
                         @RequestParam(required = false) List<BigDecimal> unitPrices,
                         RedirectAttributes redirect) {
        try {
            CreditNote note = creditNoteService.createDraft(invoiceId, reason, creditNoteDate,
                    lineInputs(descriptions, quantities, unitPrices));
            redirect.addFlashAttribute("flashSuccess", "Credit note draft created");
            return "redirect:/credit-notes/" + note.getId();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirect.addFlashAttribute("flashError", "Could not create credit note: " + exception.getMessage());
            return "redirect:/credit-notes/from-invoice/" + invoiceId + "/new";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        CreditNote note = creditNoteService.getCreditNote(id);
        model.addAttribute("creditNote", note);
        model.addAttribute("canIssue", note.getStatus() == CreditNoteStatus.DRAFT);
        model.addAttribute("refunds", refundService.getRefunds(id));
        model.addAttribute("refundableAmount", refundService.getRefundableAmount(id));
        model.addAttribute("allocations", allocationService.getAllocations(id));
        model.addAttribute("allocatableAmount", allocationService.getAvailableAmount(id));
        model.addAttribute("allocationTargets", allocationService.getEligibleTargets(id));
        model.addAttribute("bankAccounts", accountService.getAllAccounts().stream()
                .filter(account -> account.getCategory() == AccountCategory.BANK
                        && Boolean.TRUE.equals(account.getActive()))
                .toList());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("refundDate", LocalDate.now());
        model.addAttribute("allocationDate", LocalDate.now());
        return "credit-notes/detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        CreditNote note = creditNoteService.getCreditNote(id);
        if (note.getStatus() != CreditNoteStatus.DRAFT) {
            redirect.addFlashAttribute("flashError", "Only draft credit notes can be edited");
            return "redirect:/credit-notes/" + id;
        }
        Invoice invoice = note.getInvoice();
        model.addAttribute("creditNote", note);
        model.addAttribute("invoice", invoice);
        model.addAttribute("remainingCreditableAmount", creditNoteService.getRemainingCreditableAmount(invoice));
        model.addAttribute("creditNoteDate", note.getCreditNoteDate());
        model.addAttribute("reason", note.getReason());
        model.addAttribute("lines", note.getLineItems().stream()
                .map(line -> new CreditNoteService.LineInput(
                        line.getDescription(), line.getQuantity(), line.getUnitPrice()))
                .toList());
        model.addAttribute("isEdit", true);
        return "credit-notes/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @RequestParam String reason,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate creditNoteDate,
                         @RequestParam(required = false) List<String> descriptions,
                         @RequestParam(required = false) List<BigDecimal> quantities,
                         @RequestParam(required = false) List<BigDecimal> unitPrices,
                         RedirectAttributes redirect) {
        try {
            creditNoteService.updateDraft(id, reason, creditNoteDate,
                    lineInputs(descriptions, quantities, unitPrices));
            redirect.addFlashAttribute("flashSuccess", "Credit note draft updated");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirect.addFlashAttribute("flashError", "Could not update credit note: " + exception.getMessage());
        }
        return "redirect:/credit-notes/" + id;
    }

    @PostMapping("/{id}/issue")
    public String issue(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            CreditNote note = creditNoteService.issue(id);
            redirect.addFlashAttribute("flashSuccess", "Credit note " + note.getCreditNoteNumber() + " issued");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirect.addFlashAttribute("flashError", "Could not issue credit note: " + exception.getMessage());
        }
        return "redirect:/credit-notes/" + id;
    }

    @PostMapping("/{id}/refund")
    public String refund(@PathVariable Long id,
                         @RequestParam BigDecimal amount,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate refundDate,
                         @RequestParam Long bankAccountId,
                         @RequestParam PaymentMethod paymentMethod,
                         @RequestParam(required = false) String transactionId,
                         @RequestParam String reason,
                         RedirectAttributes redirect) {
        try {
            refundService.refund(id, amount, refundDate, bankAccountId, paymentMethod, transactionId, reason);
            redirect.addFlashAttribute("flashSuccess", messageSource.getMessage(
                    "creditNotes.refund.success", null, LocaleContextHolder.getLocale()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            String prefix = messageSource.getMessage(
                    "creditNotes.refund.error", null, LocaleContextHolder.getLocale());
            redirect.addFlashAttribute("flashError", prefix + " " + exception.getMessage());
        }
        return "redirect:/credit-notes/" + id;
    }

    @PostMapping("/{id}/allocate")
    public String allocate(@PathVariable Long id,
                           @RequestParam Long targetInvoiceId,
                           @RequestParam BigDecimal amount,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate allocationDate,
                           RedirectAttributes redirect) {
        try {
            allocationService.allocate(id, targetInvoiceId, amount, allocationDate);
            redirect.addFlashAttribute("flashSuccess", messageSource.getMessage(
                    "creditNotes.allocation.success", null, LocaleContextHolder.getLocale()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            String prefix = messageSource.getMessage(
                    "creditNotes.allocation.error", null, LocaleContextHolder.getLocale());
            redirect.addFlashAttribute("flashError", prefix + " " + exception.getMessage());
        }
        return "redirect:/credit-notes/" + id;
    }

    @PostMapping("/{id}/allocations/{allocationId}/reverse")
    public String reverseAllocation(@PathVariable Long id,
                                    @PathVariable Long allocationId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                    LocalDate reversalDate,
                                    @RequestParam String reversalReason,
                                    RedirectAttributes redirect) {
        try {
            allocationService.reverse(id, allocationId, reversalDate, reversalReason);
            redirect.addFlashAttribute("flashSuccess", messageSource.getMessage(
                    "creditNotes.allocation.reverseSuccess", null, LocaleContextHolder.getLocale()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            String prefix = messageSource.getMessage(
                    "creditNotes.allocation.reverseError", null, LocaleContextHolder.getLocale());
            redirect.addFlashAttribute("flashError", prefix + " " + exception.getMessage());
        }
        return "redirect:/credit-notes/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteDraft(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            CreditNote note = creditNoteService.getCreditNote(id);
            creditNoteService.deleteDraft(id);
            redirect.addFlashAttribute("flashSuccess", "Credit note draft deleted");
            return "redirect:/invoices/" + note.getInvoice().getId();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirect.addFlashAttribute("flashError", "Could not delete credit note: " + exception.getMessage());
            return "redirect:/credit-notes/" + id;
        }
    }

    private List<CreditNoteService.LineInput> lineInputs(List<String> descriptions,
                                                         List<BigDecimal> quantities,
                                                         List<BigDecimal> unitPrices) {
        if (descriptions == null || quantities == null || unitPrices == null
                || descriptions.size() != quantities.size() || descriptions.size() != unitPrices.size()) {
            throw new IllegalArgumentException("Credit note line details are incomplete");
        }
        List<CreditNoteService.LineInput> result = new ArrayList<>();
        for (int index = 0; index < descriptions.size(); index++) {
            result.add(new CreditNoteService.LineInput(
                    descriptions.get(index), quantities.get(index), unitPrices.get(index)));
        }
        return result;
    }

}
