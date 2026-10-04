package com.cogitosum.web;

import com.cogitosum.entity.*;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.service.TaxAgencyService;
import com.cogitosum.service.TaxCodeService;
import com.cogitosum.service.TaxFilingService;
import com.cogitosum.service.TaxReturnReportPdfService;
import com.cogitosum.service.CurrentCompanyContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/tax")
public class TaxWebController {
    private static final Logger log = LoggerFactory.getLogger(TaxWebController.class);

    @Autowired private TaxAgencyService agencyService;
    @Autowired private TaxCodeService codeService;
    @Autowired private TaxFilingService filingService;
    @Autowired private TaxReturnReportPdfService returnReportPdfService;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private GeneralLedgerRepository ledgerRepository;
    @Autowired private CurrentCompanyContext companyContext;
    @Autowired private MessageSource messageSource;

    @GetMapping
    public String dashboard(@RequestParam(defaultValue = "false") boolean showAll, Model model) {
        List<TaxFilingPeriod> all = filingService.getAll();
        model.addAttribute("agencies", showAll ? agencyService.getAll() : agencyService.getActiveAgencies());
        model.addAttribute("items", showAll ? codeService.getAllItems() : codeService.getActiveItems());
        model.addAttribute("codes", showAll ? codeService.getAllCodes() : codeService.getActiveCodes());
        model.addAttribute("periods", all);
        model.addAttribute("showAll", showAll);
        model.addAttribute("openCount", all.stream().filter(p -> p.getStatus() == TaxFilingStatus.OPEN || p.getStatus() == TaxFilingStatus.CALCULATED).count());
        model.addAttribute("filedCount", all.stream().filter(p -> p.getStatus() == TaxFilingStatus.FILED).count());
        model.addAttribute("active", "tax-dashboard");
        return "tax/dashboard";
    }

    @GetMapping("/agencies")
    public String agencies(@RequestParam(defaultValue = "false") boolean showAll, Model model) {
        model.addAttribute("agencies", showAll ? agencyService.getAll() : agencyService.getActiveAgencies());
        model.addAttribute("showAll", showAll);
        model.addAttribute("active", "tax-agencies");
        return "tax/agencies";
    }

    @PostMapping("/agencies")
    public String saveAgency(@RequestParam(required = false) Long id,
                             @RequestParam String code,
                             @RequestParam String name,
                             @RequestParam(required = false) String address,
                             @RequestParam(required = false) String website,
                             @RequestParam(required = false) String accountNumber,
                             @RequestParam(defaultValue = "true") Boolean active,
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
            a.setActive(active);
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
    public String codes(@RequestParam(defaultValue = "false") boolean showAll, Model model) {
        model.addAttribute("items", showAll ? codeService.getAllItems() : codeService.getActiveItems());
        model.addAttribute("codes", showAll ? codeService.getAllCodes() : codeService.getActiveCodes());
        model.addAttribute("groups", showAll ? codeService.getAllGroups() : codeService.getActiveGroups());
        model.addAttribute("agencies", showAll ? agencyService.getAll() : agencyService.getActiveAgencies());
        model.addAttribute("accounts", accountRepository.findAllByCompanyIdOrderByAccountNumberAsc(companyContext.requireCompanyId()));
        model.addAttribute("showAll", showAll);
        model.addAttribute("active", "tax-codes");
        return "tax/codes";
    }

    @PostMapping("/items")
    public String saveItem(@RequestParam(required = false) Long id,
                           @RequestParam String code,
                           @RequestParam String name,
                           @RequestParam BigDecimal rate,
                           @RequestParam Long agencyId,
                           @RequestParam(required = false) Long payableAccountId,
                           @RequestParam(required = false) Long itcAccountId,
                           @RequestParam(required = false) String salesReturnLine,
                           @RequestParam(required = false) String purchaseReturnLine,
                           @RequestParam(defaultValue = "false") Boolean forSales,
                           @RequestParam(defaultValue = "false") Boolean forPurchases,
                           @RequestParam(defaultValue = "true") Boolean active,
                           RedirectAttributes ra) {
        try {
            TaxItem item = id != null
                ? codeService.getItemById(id).orElseGet(TaxItem::new)
                : new TaxItem();
            item.setCode(code);
            item.setName(name);
            item.setRate(rate);
            item.setForSales(forSales);
            item.setForPurchases(forPurchases);
            item.setSalesReturnLine(normalizeReturnLine(salesReturnLine));
            item.setPurchaseReturnLine(normalizeReturnLine(purchaseReturnLine));
            item.setAgency(agencyService.getById(agencyId).orElseThrow());
            Long companyId = companyContext.requireCompanyId();
            item.setPayableAccount(payableAccountId != null
                    ? accountRepository.findByIdAndCompanyId(payableAccountId, companyId).orElse(null) : null);
            item.setItcAccount(itcAccountId != null
                    ? accountRepository.findByIdAndCompanyId(itcAccountId, companyId).orElse(null) : null);
            item.setActive(active);
            if (item.getId() == null) {
                codeService.createItem(item);
            } else {
                codeService.updateItem(item.getId(), item);
            }
            ra.addFlashAttribute("flashSuccess", "Tax item saved");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not save tax item: " + e.getMessage());
        }
        return "redirect:/tax/codes";
    }

    private String normalizeReturnLine(String returnLine) {
        return returnLine == null || returnLine.isBlank() ? null : returnLine.trim();
    }

    @PostMapping("/codes")
    public String saveCode(@RequestParam(required = false) Long id,
                           @RequestParam String code,
                           @RequestParam String name,
                           @RequestParam(required = false) Long salesTaxGroupId,
                           @RequestParam(required = false) Long purchaseTaxGroupId,
                           @RequestParam(defaultValue = "true") Boolean active,
                           RedirectAttributes ra) {
        try {
            TaxCode c = id != null
                ? codeService.getCodeById(id).orElseGet(TaxCode::new)
                : new TaxCode();
            c.setCode(code);
            c.setName(name);
            c.setSalesTaxGroup(salesTaxGroupId != null ? codeService.getGroupById(salesTaxGroupId).orElse(null) : null);
            c.setPurchaseTaxGroup(purchaseTaxGroupId != null ? codeService.getGroupById(purchaseTaxGroupId).orElse(null) : null);
            c.setActive(active);
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

    @PostMapping("/groups")
    public String saveGroup(@RequestParam(required = false) Long id,
                            @RequestParam String code,
                            @RequestParam String name,
                            @RequestParam(name = "itemIds", required = false) List<Long> itemIds,
                            @RequestParam(defaultValue = "true") Boolean active,
                            RedirectAttributes ra) {
        try {
            TaxGroup g = new TaxGroup();
            g.setId(id);
            g.setCode(code);
            g.setName(name);
            g.setActive(active);
            if (itemIds != null) {
                for (Long itemId : itemIds) {
                    TaxItem item = new TaxItem();
                    item.setId(itemId);
                    g.getTaxItems().add(item);
                }
            }
            if (g.getId() == null) {
                codeService.createGroup(g);
            } else {
                codeService.updateGroup(g.getId(), g);
            }
            ra.addFlashAttribute("flashSuccess", "Tax group saved");
        } catch (Exception e) {
            log.error("Could not save tax group (id={}, code={}, name={}, itemIds={})",
                    id, code, name, itemIds, e);
            String detail = e.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = messageSource.getMessage("tax.group.saveErrorNoDetails",
                        new Object[]{e.getClass().getSimpleName()}, LocaleContextHolder.getLocale());
            }
            ra.addFlashAttribute("flashError", messageSource.getMessage(
                    "tax.group.saveError", new Object[]{detail}, LocaleContextHolder.getLocale()));
        }
        return "redirect:/tax/codes";
    }

    @PostMapping("/items/delete/{id}")
    public String deleteItem(@PathVariable Long id, RedirectAttributes ra) {
        try {
            codeService.deleteItem(id);
            ra.addFlashAttribute("flashSuccess", "Tax item deactivated");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not deactivate tax item: " + e.getMessage());
        }
        return "redirect:/tax/codes";
    }

    @PostMapping("/codes/delete/{id}")
    public String deleteCode(@PathVariable Long id, RedirectAttributes ra) {
        try {
            codeService.deleteCode(id);
            ra.addFlashAttribute("flashSuccess", "Tax code deactivated");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not deactivate tax code: " + e.getMessage());
        }
        return "redirect:/tax/codes";
    }

    @PostMapping("/groups/delete/{id}")
    public String deleteGroup(@PathVariable Long id, RedirectAttributes ra) {
        try {
            codeService.deleteGroup(id);
            ra.addFlashAttribute("flashSuccess", "Tax group deactivated");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not deactivate tax group: " + e.getMessage());
        }
        return "redirect:/tax/codes";
    }

    @PostMapping("/agencies/delete/{id}")
    public String deleteAgency(@PathVariable Long id, RedirectAttributes ra) {
        try {
            agencyService.deleteAgency(id);
            ra.addFlashAttribute("flashSuccess", "Agency deactivated");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not deactivate agency: " + e.getMessage());
        }
        return "redirect:/tax/agencies";
    }

    @GetMapping("/periods")
    public String periods(Model model) {
        model.addAttribute("periods", filingService.getAll());
        model.addAttribute("agencies", agencyService.getAll());
        model.addAttribute("active", "tax-periods");
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
                model.addAttribute("collectedDetail", filingService.getTaxCollectedDetail(p.getAgency(), p.getPeriodStart(), p.getPeriodEnd()));
                model.addAttribute("itcDetail", filingService.getItcDetail(p.getAgency(), p.getPeriodStart(), p.getPeriodEnd()));
                var returnLines = filingService.getReturnLineBreakdown(p);
                model.addAttribute("taxReturnRows", filingService.getTaxReturnRows(p, returnLines));
                model.addAttribute("hasUnmappedReturnLineAmounts",
                        filingService.hasUnmappedReturnLineAmounts(p, returnLines));
                model.addAttribute("bankAccounts", accountRepository.findByCompanyIdAndAccountTypeOrderByAccountNumberAsc(
                        companyContext.requireCompanyId(), AccountType.ASSET));
                model.addAttribute("active", "tax-agency-detail-report");
                return "tax/period-detail";
            })
            .orElseGet(() -> {
                ra.addFlashAttribute("flashError", "Period not found");
                return "redirect:/tax/periods";
            });
    }

    @GetMapping("/periods/{id}/lines/{line}")
    public String periodLineDetail(@PathVariable Long id,
                                   @PathVariable String line,
                                   Model model,
                                   RedirectAttributes ra) {
        TaxFilingPeriod period = filingService.getById(id).orElse(null);
        if (period == null) {
            ra.addFlashAttribute("flashError", "Period not found");
            return "redirect:/tax/periods";
        }
        try {
            var returnLines = filingService.getReturnLineBreakdown(period);
            var rows = filingService.getTaxReturnRows(period, returnLines);
            var selectedRow = rows.stream()
                    .filter(row -> "payable".equals(line)
                            ? "tax.detail.returnLinePayable".equals(row.descriptionKey())
                            : line.equals(row.line()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Tax return line not found"));
            var details = filingService.getReturnLineDetails(period, line);
            BigDecimal amount = selectedRow.balance() == null
                    ? selectedRow.amount() : selectedRow.balance();
            BigDecimal detailTotal = details.stream()
                    .map(detail -> detail.amount() == null ? BigDecimal.ZERO : detail.amount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            model.addAttribute("period", period);
            model.addAttribute("returnRow", selectedRow);
            model.addAttribute("lineAmount", amount);
            model.addAttribute("lineDetails", details);
            model.addAttribute("detailTotal", detailTotal);
            model.addAttribute("active", "tax-agency-detail-report");
            return "tax/period-line-detail";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", messageSource.getMessage(
                    "tax.detail.lineNotFound", null, LocaleContextHolder.getLocale()));
            return "redirect:/tax/periods/" + id;
        }
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
                       RedirectAttributes ra) {
        try {
            filingService.file(id, "portal");
            ra.addFlashAttribute("flashSuccess", "Return filed. Filing journal posted.");
            return "redirect:/tax/periods/" + id + "/report";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not file: " + e.getMessage());
            return "redirect:/tax/periods/" + id;
        }
    }

    @GetMapping("/periods/{id}/report")
    public ResponseEntity<byte[]> filedReturnReport(@PathVariable Long id) {
        return filingService.getFiledReturnReport(id)
                .map(report -> {
                    byte[] pdf = returnReportPdfService.create(report, LocaleContextHolder.getLocale());
                    String filename = "tax-return-" + id + ".pdf";
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    ContentDisposition.attachment().filename(filename).build().toString())
                            .body(pdf);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
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
            for (TaxItem item : codeService.getItemsByAgency(agency.getId())) {
                if (item.getPayableAccount() == null) continue;
                Map<String, Object> row = new HashMap<>();
                row.put("agency", agency);
                row.put("code", item);
                row.put("account", item.getPayableAccount());
                BigDecimal balance = ledgerRepository.findByCompanyIdAndAccountId(
                        companyContext.requireCompanyId(), item.getPayableAccount().getId())
                    .map(gl -> gl.getCreditBalance().subtract(gl.getDebitBalance()))
                    .orElse(BigDecimal.ZERO);
                row.put("glBalance", balance);
                rows.add(row);
            }
        }
        model.addAttribute("rows", rows);
        model.addAttribute("active", "tax-reconciliation");
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
                    .map(p -> p.getTaxItc() == null ? BigDecimal.ZERO : p.getTaxItc())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalNet = periods.stream()
                    .map(p -> p.getNetOwing() == null ? BigDecimal.ZERO : p.getNetOwing())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                model.addAttribute("agency", a);
                model.addAttribute("periods", periods);
                model.addAttribute("totalCollected", totalCollected);
                model.addAttribute("totalItc", totalItc);
                model.addAttribute("totalNet", totalNet);
                model.addAttribute("active", "tax-agency-report");
                return "tax/agency-report";
            })
            .orElseGet(() -> {
                ra.addFlashAttribute("flashError", "Agency not found");
                return "redirect:/tax/agencies";
            });
    }
}
