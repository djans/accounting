package com.cogitosum.web;

import com.cogitosum.entity.Customer;
import com.cogitosum.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customers")
public class CustomerWebController {

    @Autowired
    private CustomerService customerService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("customers", customerService.getAllCustomers());
        return "customers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        Customer c = new Customer();
        c.setCountry("Canada");
        model.addAttribute("customer", c);
        model.addAttribute("isNew", true);
        return "customers/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return customerService.getCustomerById(id)
                .map(c -> {
                    model.addAttribute("customer", c);
                    model.addAttribute("isNew", false);
                    return "customers/form";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("flashError", "Customer not found");
                    return "redirect:/customers";
                });
    }

    @PostMapping
    public String create(@ModelAttribute Customer customer, RedirectAttributes ra) {
        try {
            customerService.createCustomer(customer);
            ra.addFlashAttribute("flashSuccess", "Customer created");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not create customer: " + e.getMessage());
        }
        return "redirect:/customers";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Customer customer, RedirectAttributes ra) {
        try {
            customerService.updateCustomer(id, customer);
            ra.addFlashAttribute("flashSuccess", "Customer updated");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not update customer: " + e.getMessage());
        }
        return "redirect:/customers";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            customerService.deleteCustomer(id);
            ra.addFlashAttribute("flashSuccess", "Customer deleted");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not delete customer: " + e.getMessage());
        }
        return "redirect:/customers";
    }
}
