package com.cogitosum.web;

import com.cogitosum.entity.Company;
import com.cogitosum.service.CompanyService;
import com.cogitosum.service.CurrentCompanyContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

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
        var companies = companyService.getAllCompanies();
        var manageableCompanyIds = companies.stream()
            .filter(company -> companyContext.canAdministerCompany(company.getId()))
            .map(Company::getId)
            .collect(java.util.stream.Collectors.toSet());
        boolean superAdmin = companyContext.isSuperAdmin();
        model.addAttribute("companies", companies);
        model.addAttribute("currentCompanyId", companyContext.requireCompanyId());
        model.addAttribute("manageableCompanyIds", manageableCompanyIds);
        model.addAttribute("canManageCompanies", manageableCompanyIds.contains(companyContext.requireCompanyId()));
        model.addAttribute("canCreateCompany", superAdmin);
        model.addAttribute("canManagePostedJournalEditing", !manageableCompanyIds.isEmpty());
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
        companyContext.requireSuperAdmin();
        model.addAttribute("company", new Company());
        model.addAttribute("sourceCompanies", companyService.getAllCompanies());
        model.addAttribute("isEdit", false);
        return "companies/form";
    }

    @GetMapping("/{id}/edit")
    public String editCompany(@PathVariable Long id, Model model) {
        model.addAttribute("company", companyService.getCompanyForEdit(id));
        model.addAttribute("isEdit", true);
        return "companies/form";
    }

    @GetMapping("/{id}/logo")
    @ResponseBody
    public ResponseEntity<byte[]> companyLogo(@PathVariable Long id) {
        Company company = companyService.getCompanyForEdit(id);
        byte[] logo = company.getLogoData();
        if (logo == null || company.getLogoContentType() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(company.getLogoContentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(logo);
    }

    @PostMapping
    public String create(@ModelAttribute("company") Company company,
                         @RequestParam(required = false) Long sourceCompanyId,
                         @RequestParam(value = "logo", required = false) MultipartFile logo,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        companyContext.requireSuperAdmin();
        try {
            companyService.createCompany(company, sourceCompanyId, logoBytes(logo), logoContentType(logo));
            redirectAttributes.addFlashAttribute("companyNotice", "company.created");
            return "redirect:/companies";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("companyError", "company.validation.error");
            model.addAttribute("sourceCompanies", companyService.getAllCompanies());
            model.addAttribute("isEdit", false);
            return "companies/form";
        }
    }

    @PostMapping("/{id}")
    public String updateCompany(@PathVariable Long id,
                                @ModelAttribute("company") Company company,
                                @RequestParam(value = "logo", required = false) MultipartFile logo,
                                @RequestParam(defaultValue = "false") boolean removeLogo,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            companyService.updateCompany(id, company, logoBytes(logo), logoContentType(logo), removeLogo);
            redirectAttributes.addFlashAttribute("companyNotice", "company.updated");
            return "redirect:/companies";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("companyError", "company.validation.error");
            model.addAttribute("isEdit", true);
            return "companies/form";
        }
    }

    private byte[] logoBytes(MultipartFile logo) {
        if (logo == null || logo.isEmpty()) {
            return null;
        }
        try {
            return logo.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read the company logo", exception);
        }
    }

    private String logoContentType(MultipartFile logo) {
        return logo == null || logo.isEmpty() ? null : logo.getContentType();
    }
}
