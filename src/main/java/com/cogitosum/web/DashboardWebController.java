package com.cogitosum.web;

import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.service.AccountingReportService;
import com.cogitosum.service.CustomerService;
import com.cogitosum.service.GeneralJournalService;
import com.cogitosum.service.InvoiceService;
import com.cogitosum.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.Map;

@Controller
public class DashboardWebController {

    @Autowired private CustomerService customerService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private PaymentService paymentService;
    @Autowired private GeneralJournalService journalService;
    @Autowired private AccountingReportService reportService;

    @GetMapping("/")
    public String dashboard(Model model) {
        long customers = customerService.getAllCustomers().size();
        long invoices = invoiceService.getAllInvoices().size();
        long overdue = invoiceService.getOverdueInvoices().size();
        long payments = paymentService.getAllPayments().size();
        long postedJournals = journalService.getJournalsByStatus(JournalStatus.POSTED).size();
        long draftJournals = journalService.getJournalsByStatus(JournalStatus.DRAFT).size();

        BigDecimal openReceivable = invoiceService.getAllInvoices().stream()
                .filter(inv -> inv.getStatus() != InvoiceStatus.PAID
                        && inv.getStatus() != InvoiceStatus.CANCELLED
                        && inv.getStatus() != InvoiceStatus.REFUNDED)
                .map(inv -> inv.getTotalAmount().subtract(
                        inv.getPaidAmount() == null ? BigDecimal.ZERO : inv.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> trialBalance = reportService.getTrialBalance();

        model.addAttribute("customers", customers);
        model.addAttribute("invoices", invoices);
        model.addAttribute("overdue", overdue);
        model.addAttribute("payments", payments);
        model.addAttribute("postedJournals", postedJournals);
        model.addAttribute("draftJournals", draftJournals);
        model.addAttribute("openReceivable", openReceivable);
        model.addAttribute("trialBalance", trialBalance);
        model.addAttribute("recentInvoices",
                invoiceService.getAllInvoices().stream().limit(5).toList());
        return "dashboard";
    }
}
