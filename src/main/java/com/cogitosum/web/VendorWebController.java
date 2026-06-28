package com.cogitosum.web;

import com.cogitosum.entity.Vendor;
import com.cogitosum.service.VendorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/vendors")
public class VendorWebController {

    @Autowired
    private VendorService vendorService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("vendors", vendorService.getAllVendors());
        return "vendors/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        Vendor v = new Vendor();
        v.setCountry("Canada");
        model.addAttribute("vendor", v);
        model.addAttribute("isNew", true);
        return "vendors/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return vendorService.getVendorById(id)
                .map(v -> {
                    model.addAttribute("vendor", v);
                    model.addAttribute("isNew", false);
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
