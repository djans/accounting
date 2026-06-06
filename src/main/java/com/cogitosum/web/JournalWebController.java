package com.cogitosum.web;

import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.service.GeneralJournalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/journals")
public class JournalWebController {

    @Autowired private GeneralJournalService journalService;
    @Autowired private ChartOfAccountService accountService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("journals", journalService.getAllJournals());
        return "journals/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("accounts", accountService.getActiveAccounts());
        return "journals/form";
    }

    @PostMapping
    public String create(@RequestParam String narrative,
                         @RequestParam(required = false) String reference,
                         @RequestParam List<Long> accountIds,
                         @RequestParam List<BigDecimal> debits,
                         @RequestParam List<BigDecimal> credits,
                         @RequestParam(required = false) List<String> descriptions,
                         RedirectAttributes ra) {
        try {
            GeneralJournal journal = new GeneralJournal();
            journal.setNarrative(narrative);
            journal.setReference(reference);

            List<JournalEntry> entries = new ArrayList<>();
            for (int i = 0; i < accountIds.size(); i++) {
                Long accId = accountIds.get(i);
                if (accId == null) continue;
                ChartOfAccount account = accountService.getAccountById(accId)
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accId));
                BigDecimal d = i < debits.size() && debits.get(i) != null ? debits.get(i) : BigDecimal.ZERO;
                BigDecimal c = i < credits.size() && credits.get(i) != null ? credits.get(i) : BigDecimal.ZERO;
                if (d.compareTo(BigDecimal.ZERO) == 0 && c.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                JournalEntry e = new JournalEntry();
                e.setAccount(account);
                e.setDebit(d);
                e.setCredit(c);
                e.setLineNumber(entries.size() + 1);
                if (descriptions != null && i < descriptions.size()) {
                    e.setDescription(descriptions.get(i));
                }
                entries.add(e);
            }
            if (entries.size() < 2) {
                throw new IllegalArgumentException("A journal needs at least two entries with amounts");
            }
            journal.setEntries(entries);

            GeneralJournal saved = journalService.createJournal(journal);
            ra.addFlashAttribute("flashSuccess", "Journal " + saved.getJournalNumber() + " created as DRAFT");
            return "redirect:/journals/" + saved.getId();
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not create journal: " + e.getMessage());
            return "redirect:/journals/new";
        }
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
            GeneralJournal reversal = journalService.reverseJournal(id, reason);
            ra.addFlashAttribute("flashSuccess",
                    "Reversal posted as " + (reversal != null ? reversal.getJournalNumber() : ""));
            return "redirect:/journals/" + (reversal != null ? reversal.getId() : id);
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not reverse: " + e.getMessage());
            return "redirect:/journals/" + id;
        }
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
