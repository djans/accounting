package com.cogitosum.web;

import com.cogitosum.entity.Vendor;
import com.cogitosum.service.VendorService;
import com.cogitosum.service.ChartOfAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/vendors")
public class VendorWebController {

    @Autowired
    private VendorService vendorService;
    @Autowired private ChartOfAccountService accountService;

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        String query = q == null ? "" : q.trim();
        var vendors = vendorService.getAllVendors();
        if (!query.isBlank()) {
            String normalized = query.toLowerCase(Locale.ROOT);
            vendors = vendors.stream()
                    .filter(v -> contains(v.getBusinessName(), normalized)
                            || contains(v.getName(), normalized)
                            || contains(v.getEmail(), normalized)
                            || contains(v.getMainPhone(), normalized)
                            || contains(v.getWorkPhone(), normalized)
                            || contains(v.getMobilePhone(), normalized)
                            || contains(v.getProvince(), normalized)
                            || contains(v.getVendorType(), normalized))
                    .collect(Collectors.toList());
        }
        model.addAttribute("vendors", vendors);
        model.addAttribute("searchQuery", query);
        return "vendors/list";
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        Vendor v = new Vendor();
        v.setCountry("Canada");
        model.addAttribute("vendor", v);
        model.addAttribute("isNew", true);
        model.addAttribute("accounts", accountService.getActiveAccounts());
        return "vendors/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return vendorService.getVendorById(id)
                .map(v -> {
                    model.addAttribute("vendor", v);
                    model.addAttribute("isNew", false);
                    model.addAttribute("accounts", accountService.getActiveAccounts());
                    return "vendors/form";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("flashError", "Vendor not found");
                    return "redirect:/vendors";
                });
    }

    @PostMapping
    public String create(@ModelAttribute Vendor vendor, RedirectAttributes ra) {
        try {
            vendorService.createVendor(vendor);
            ra.addFlashAttribute("flashSuccess", "Vendor created");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not create vendor: " + e.getMessage());
        }
        return "redirect:/vendors";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Vendor vendor, RedirectAttributes ra) {
        try {
            vendorService.updateVendor(id, vendor);
            ra.addFlashAttribute("flashSuccess", "Vendor updated");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not update vendor: " + e.getMessage());
        }
        return "redirect:/vendors";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            vendorService.deleteVendor(id);
            ra.addFlashAttribute("flashSuccess", "Vendor deleted");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not delete vendor: " + e.getMessage());
        }
        return "redirect:/vendors";
    }
}
