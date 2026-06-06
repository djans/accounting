package com.cogitosum.web;

import com.cogitosum.entity.*;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.service.TaxAgencyService;
import com.cogitosum.service.TaxCodeService;
import com.cogitosum.service.TaxFilingService;
import org.springframework.beans.factory.annotation.Autowired;
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

@Controller
@RequestMapping("/tax")
public class TaxWebController {

    @Autowired private TaxAgencyService agencyService;
    @Autowired private TaxCodeService codeService;
    @Autowired private TaxFilingService filingService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private GeneralLedgerRepository ledgerRepository;

    @GetMapping
    public String dashboard(Model model) {
        List<TaxFilingPeriod> all = filingService.getAll();
        model.addAttribute("agencies", agencyService.getAll());
        model.addAttribute("codes", codeService.getAll());
        model.addAttribute("periods", all);
        model.addAttribute("openCount", all.stream().filter(p -> p.getStatus() == TaxFilingStatus.OPEN || p.getStatus() == TaxFilingStatus.CALCULATED).count());
        model.addAttribute("filedCount", all.stream().filter(p -> p.getStatus() == TaxFilingStatus.FILED).count());
        return "tax/dashboard";
    }

    @GetMapping("/agencies")
    public String agencies(Model model) {
        model.addAttribute("agencies", agencyService.getAll());
        return "tax/agencies";
    }

    @PostMapping("/agencies")
    public String saveAgency(@RequestParam(required = false) Long id,
                             @RequestParam String code,
                             @RequestParam String name,
                             @RequestParam(required = false) String address,
                             @RequestParam(required = false) String website,
                             @RequestParam(required = false) String accountNumber,
                             RedirectAttributes ra) {
        try {
            TaxAgency a = id != null
                ? agencyService.getById(id).orElseGet(TaxAgency::new)
                : new TaxAgency();
            a.setCode(code);
            a.setName(name);
            a.setAddress(address);
            a.setWebsite(website);
            a.setAccountNumber(accountNumber);
            if (a.getId() == null) {
                agencyService.createAgency(a);
            } else {
                agencyService.updateAgency(a.getId(), a);
            }
            ra.addFlashAttribute("flashSuccess", "Agency saved");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not save agency: " + e.getMessage());
        }
        return "redirect:/tax/agencies";
    }

    @GetMapping("/codes")
    public String codes(Model model) {
        model.addAttribute("codes", codeService.getAll());
        model.addAttribute("agencies", agencyService.getAll());
        model.addAttribute("accounts", accountRepository.findAll());
        return "tax/codes";
    }

    @PostMapping("/codes")
    public String saveCode(@RequestParam(required = false) Long id,
                           @RequestParam String code,
                           @RequestParam String name,
                           @RequestParam BigDecimal rate,
                           @RequestParam Long agencyId,
                           @RequestParam(required = false) Long payableAccountId,
                           RedirectAttributes ra) {
        try {
            TaxCode c = id != null
                ? codeService.getById(id).orElseGet(TaxCode::new)
                : new TaxCode();
            c.setCode(code);
            c.setName(name);
            c.setRate(rate);
            c.setAgency(agencyService.getById(agencyId).orElseThrow());
            if (payableAccountId != null) {
                c.setPayableAccount(accountRepository.findById(payableAccountId).orElse(null));
            }
            c.setActive(true);
            if (c.getId() == null) {
                codeService.createCode(c);
            } else {
                codeService.updateCode(c.getId(), c);
            }
            ra.addFlashAttribute("flashSuccess", "Tax code saved");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not save tax code: " + e.getMessage());
        }
        return "redirect:/tax/codes";
    }

    @GetMapping("/periods")
    public String periods(Model model) {
        model.addAttribute("periods", filingService.getAll());
        model.addAttribute("agencies", agencyService.getAll());
        return "tax/periods";
    }

    @PostMapping("/periods")
    public String createPeriod(@RequestParam Long agencyId,
                               @RequestParam LocalDate periodStart,
                               @RequestParam LocalDate periodEnd,
                               RedirectAttributes ra) {
        try {
            TaxFilingPeriod p = filingService.createPeriod(agencyId, periodStart, periodEnd);
            filingService.calculate(p.getId());
            ra.addFlashAttribute("flashSuccess", "Filing period created");
            return "redirect:/tax/periods/" + p.getId();
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not create period: " + e.getMessage());
            return "redirect:/tax/periods";
        }
    }

    @GetMapping("/periods/{id}")
    public String periodDetail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return filingService.getById(id)
            .map(p -> {
                model.addAttribute("period", p);
                model.addAttribute("bankAccounts", accountRepository.findByAccountType(AccountType.ASSET));
                return "tax/period-detail";
            })
            .orElseGet(() -> {
                ra.addFlashAttribute("flashError", "Period not found");
                return "redirect:/tax/periods";
            });
    }

    @PostMapping("/periods/{id}/calculate")
    public String calculate(@PathVariable Long id, RedirectAttributes ra) {
        try {
            filingService.calculate(id);
            ra.addFlashAttribute("flashSuccess", "Totals recalculated from invoices in period");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not recalculate: " + e.getMessage());
        }
        return "redirect:/tax/periods/" + id;
    }

    @PostMapping("/periods/{id}/file")
    public String file(@PathVariable Long id,
                       @RequestParam(defaultValue = "0") BigDecimal itcAmount,
                       RedirectAttributes ra) {
        try {
            filingService.file(id, itcAmount, "portal");
            ra.addFlashAttribute("flashSuccess", "Return filed. Filing journal posted.");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not file: " + e.getMessage());
        }
        return "redirect:/tax/periods/" + id;
    }

    @PostMapping("/periods/{id}/pay")
    public String pay(@PathVariable Long id,
                      @RequestParam String bankAccountNumber,
                      @RequestParam(required = false) LocalDate paymentDate,
                      RedirectAttributes ra) {
        try {
            filingService.recordPayment(id, bankAccountNumber, paymentDate, "portal");
            ra.addFlashAttribute("flashSuccess", "Payment recorded. Payment journal posted.");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not record payment: " + e.getMessage());
        }
        return "redirect:/tax/periods/" + id;
    }

    @PostMapping("/periods/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        try {
            filingService.cancel(id);
            ra.addFlashAttribute("flashSuccess", "Period cancelled");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not cancel: " + e.getMessage());
        }
        return "redirect:/tax/periods/" + id;
    }

    @GetMapping("/reconciliation")
    public String reconciliation(Model model) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TaxAgency agency : agencyService.getAll()) {
            for (TaxCode code : codeService.getByAgency(agency.getId())) {
                if (code.getPayableAccount() == null) continue;
                Map<String, Object> row = new HashMap<>();
                row.put("agency", agency);
                row.put("code", code);
                row.put("account", code.getPayableAccount());
                BigDecimal balance = ledgerRepository.findByAccountId(code.getPayableAccount().getId())
                    .map(gl -> gl.getCreditBalance().subtract(gl.getDebitBalance()))
                    .orElse(BigDecimal.ZERO);
                row.put("glBalance", balance);
                rows.add(row);
            }
        }
        model.addAttribute("rows", rows);
        return "tax/reconciliation";
    }

    @GetMapping("/agencies/{id}/report")
    public String agencyReport(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return agencyService.getById(id)
            .map(a -> {
                List<TaxFilingPeriod> periods = filingService.getByAgency(id);
                BigDecimal totalCollected = periods.stream()
                    .map(p -> p.getTaxCollected() == null ? BigDecimal.ZERO : p.getTaxCollected())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalItc = periods.stream()
                    .map(p -> p.getTaxITC() == null ? BigDecimal.ZERO : p.getTaxITC())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalNet = periods.stream()
                    .map(p -> p.getNetOwing() == null ? BigDecimal.ZERO : p.getNetOwing())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                model.addAttribute("agency", a);
                model.addAttribute("periods", periods);
                model.addAttribute("totalCollected", totalCollected);
                model.addAttribute("totalItc", totalItc);
                model.addAttribute("totalNet", totalNet);
                return "tax/agency-report";
            })
            .orElseGet(() -> {
                ra.addFlashAttribute("flashError", "Agency not found");
                return "redirect:/tax/agencies";
            });
    }
}
