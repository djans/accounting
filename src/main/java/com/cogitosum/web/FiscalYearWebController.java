package com.cogitosum.web;

import com.cogitosum.entity.FiscalYear;
import com.cogitosum.service.FiscalYearCloseService;
import com.cogitosum.service.FiscalYearService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/fiscal")
public class FiscalYearWebController {

    @Autowired private FiscalYearService fiscalYearService;
    @Autowired private FiscalYearCloseService fiscalYearCloseService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("fiscalYears", fiscalYearService.getAll());
        return "fiscal/list";
    }

    @PostMapping
    public String create(@RequestParam String label,
                         @RequestParam LocalDate startDate,
                         @RequestParam LocalDate endDate,
                         RedirectAttributes ra) {
        try {
            FiscalYear fy = new FiscalYear();
            fy.setLabel(label);
            fy.setStartDate(startDate);
            fy.setEndDate(endDate);
            fiscalYearService.createFiscalYear(fy);
            ra.addFlashAttribute("flashSuccess", "Exercice créé");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Impossible de créer l'exercice : " + e.getMessage());
        }
        return "redirect:/fiscal";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return fiscalYearService.getById(id)
                .map(fy -> {
                    model.addAttribute("fiscalYear", fy);
                    return "fiscal/detail";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("flashError", "Exercice introuvable");
                    return "redirect:/fiscal";
                });
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, RedirectAttributes ra) {
        try {
            fiscalYearCloseService.close(id, "portal");
            ra.addFlashAttribute("flashSuccess", "Exercice clôturé. Écriture de clôture comptabilisée.");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Clôture impossible : " + e.getMessage());
        }
        return "redirect:/fiscal/" + id;
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, RedirectAttributes ra) {
        try {
            fiscalYearCloseService.reopen(id);
            ra.addFlashAttribute("flashSuccess", "Exercice rouvert. Écriture de clôture contrepassée.");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Réouverture impossible : " + e.getMessage());
        }
        return "redirect:/fiscal/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            fiscalYearService.deleteFiscalYear(id);
            ra.addFlashAttribute("flashSuccess", "Exercice supprimé");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Suppression impossible : " + e.getMessage());
        }
        return "redirect:/fiscal";
    }
}
