package com.cogitosum.web;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.UserRole;
import com.cogitosum.service.CompanyService;
import com.cogitosum.service.CurrentCompanyContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/companies")
public class CompanyWebController {

    private final CompanyService companyService;
    private final CurrentCompanyContext companyContext;

    public CompanyWebController(CompanyService companyService, CurrentCompanyContext companyContext) {
        this.companyService = companyService;
        this.companyContext = companyContext;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("companies", companyService.getAllCompanies());
        model.addAttribute("currentCompanyId", companyContext.requireCompanyId());
        boolean canManageCompanies = companyContext.currentUser().getRole() == UserRole.ADMIN;
        model.addAttribute("canCreateCompany", canManageCompanies);
        model.addAttribute("canManagePostedJournalEditing", canManageCompanies);
        return "companies/list";
    }

    @PostMapping("/{id}/posted-journal-editing")
    public String setPostedJournalEditing(@PathVariable Long id,
                                          @RequestParam boolean enabled,
                                          RedirectAttributes redirectAttributes) {
        companyService.setPostedJournalEditingEnabled(id, enabled);
        redirectAttributes.addFlashAttribute("companyNotice",
                enabled ? "company.postedJournalEditing.enabled" : "company.postedJournalEditing.disabled");
        return "redirect:/companies";
    }

    @PostMapping("/{id}/select")
    public String select(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (!companyContext.canAccessCompany(id)) {
            throw new AccessDeniedException("The requested company is not available to this user");
        }
        companyContext.selectCompany(id);
        redirectAttributes.addFlashAttribute("companyNotice", "company.switched");
        return "redirect:/companies";
    }

    @GetMapping("/new")
    public String newCompany(Model model) {
        model.addAttribute("company", new Company());
        model.addAttribute("sourceCompanies", companyService.getAllCompanies());
        return "companies/form";
    }

    @PostMapping
    public String create(@ModelAttribute("company") Company company,
                         @RequestParam(required = false) Long sourceCompanyId,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            companyService.createCompany(company, sourceCompanyId);
            redirectAttributes.addFlashAttribute("companyNotice", "company.created");
            return "redirect:/companies";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("companyError", "company.validation.error");
            model.addAttribute("sourceCompanies", companyService.getAllCompanies());
            return "companies/form";
        }
    }
}
