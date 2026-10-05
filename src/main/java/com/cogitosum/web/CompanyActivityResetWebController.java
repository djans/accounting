package com.cogitosum.web;

import com.cogitosum.service.CompanyActivityResetService;
import com.cogitosum.service.CompanyActivityResetService.ActivityCounts;
import com.cogitosum.service.CurrentCompanyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/reset")
public class CompanyActivityResetWebController {

    private static final Logger log = LoggerFactory.getLogger(CompanyActivityResetWebController.class);

    private final CompanyActivityResetService resetService;
    private final CurrentCompanyContext companyContext;
    private final MessageSource messageSource;

    public CompanyActivityResetWebController(
            CompanyActivityResetService resetService,
            CurrentCompanyContext companyContext,
            MessageSource messageSource) {
        this.resetService = resetService;
        this.companyContext = companyContext;
        this.messageSource = messageSource;
    }

    @GetMapping
    public String page(Authentication authentication, Model model) {
        requireAdmin(authentication);
        model.addAttribute("counts", resetService.getCurrentCompanyCounts());
        model.addAttribute("company", companyContext.requireCompany());
        model.addAttribute("active", "admin-reset");
        return "admin/reset";
    }

    @PostMapping
    public String reset(@RequestParam String confirmation,
                        @RequestParam(defaultValue = "false") boolean confirmReset,
                        Authentication authentication,
                        RedirectAttributes attributes) {
        requireAdmin(authentication);
        if (!"RESET".equals(confirmation) || !confirmReset) {
            attributes.addFlashAttribute("flashError",
                    messageSource.getMessage("admin.reset.confirmationRequired",
                            null, LocaleContextHolder.getLocale()));
            return "redirect:/admin/reset";
        }

        ActivityCounts counts = resetService.resetCurrentCompany();
        log.warn("Company activity reset: actor={} company={} deleted={}",
                authentication.getName(), companyContext.requireCompanyId(), counts);
        attributes.addFlashAttribute("flashSuccess", messageSource.getMessage(
                "admin.reset.success",
                new Object[]{counts.invoices(), counts.customerPayments(), counts.bills(),
                        counts.vendorPayments(), counts.cheques(), counts.creditCardCharges(),
                        counts.transfers(), counts.journals(), counts.taxFilingPeriods(), counts.fiscalYears(),
                        counts.importedBankTransactions(), counts.reconciliationSessions()},
                LocaleContextHolder.getLocale()));
        return "redirect:/admin/reset";
    }

    @PostMapping("/reconciliation")
    public String resetBankReconciliation(@RequestParam String confirmation,
                                          @RequestParam(defaultValue = "false") boolean confirmReset,
                                          Authentication authentication,
                                          RedirectAttributes attributes) {
        requireAdmin(authentication);
        if (!"RESET BANK".equals(confirmation) || !confirmReset) {
            attributes.addFlashAttribute("flashError",
                    messageSource.getMessage("admin.reset.bankConfirmationRequired",
                            null, LocaleContextHolder.getLocale()));
            return "redirect:/admin/reset";
        }

        CompanyActivityResetService.BankReconciliationCounts counts =
                resetService.resetBankReconciliationForCurrentCompany();
        log.warn("Company bank reconciliation reset: actor={} company={} deleted={}",
                authentication.getName(), companyContext.requireCompanyId(), counts);
        attributes.addFlashAttribute("flashSuccess", messageSource.getMessage(
                "admin.reset.bankSuccess",
                new Object[]{counts.importedBankTransactions(), counts.reconciliationSessions()},
                LocaleContextHolder.getLocale()));
        return "redirect:/admin/reset";
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required");
        }
    }
}
