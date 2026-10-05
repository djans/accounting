package com.cogitosum.web;

import com.cogitosum.dto.CustomerPaymentDetailsDTO;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.Payment;
import com.cogitosum.entity.PaymentMethod;
import com.cogitosum.entity.PaymentStatus;
import com.cogitosum.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentWebController.class)
public class PaymentWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private InvoiceService invoiceService;

    @MockitoBean
    private ChartOfAccountService accountService;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private TaxFilingService taxFilingService;

    @Test
    public void paymentListShowsDateAndCustomerFilters() throws Exception {
        Customer customer = new Customer();
        customer.setId(4L);
        customer.setBusinessName("Test Customer");
        Invoice invoice = new Invoice();
        invoice.setId(7L);
        invoice.setInvoiceNumber("INV-7");
        invoice.setCustomer(customer);
        Payment payment = new Payment();
        payment.setId(3L);
        payment.setInvoice(invoice);
        payment.setTransactionId("TXN-3");
        payment.setPaymentDate(LocalDate.of(2026, 10, 4));
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("25.00"));
        when(paymentService.getAllPayments()).thenReturn(List.of(payment));
        when(taxFilingService.getDateFilterPeriods(any(LocalDate.class))).thenReturn(
                new TaxFilingService.FilterPeriodRanges(
                        new TaxFilingService.DateRange("CRA", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)),
                        new TaxFilingService.DateRange("CRA", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30))));

        mockMvc.perform(get("/payments"))
                .andExpect(status().isOk())
                .andExpect(view().name("payments/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-date-preset")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-customer-filter")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Customer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-list-customer-id=\"4\"")));
    }

    @Test
    public void customerPaymentForm_ReturnsView() throws Exception {
        mockMvc.perform(get("/payments/customer"))
                .andExpect(status().isOk())
                .andExpect(view().name("payments/customer_payment"))
                .andExpect(model().attributeExists("customers", "methods", "bankAccounts", "arAccounts"))
                .andExpect(model().attributeExists("defaultBankAccountId", "defaultArAccountId"));
    }

    @Test
    public void newPaymentForm_UsesOnlyInvoicesWithOutstandingBalances() throws Exception {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-OPEN");
        invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        invoice.setTotalAmount(new BigDecimal("100.00"));
        invoice.setPaidAmount(new BigDecimal("40.00"));
        Customer customer = new Customer();
        customer.setBusinessName("Test Corp");
        invoice.setCustomer(customer);
        when(invoiceService.getUnpaidInvoices()).thenReturn(List.of(invoice));
        when(accountService.getAllAccounts()).thenReturn(List.of());

        mockMvc.perform(get("/payments/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("payments/form"))
                .andExpect(model().attribute("invoices", List.of(invoice)))
                .andExpect(model().attributeExists("paymentDate"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"paymentDate\"")));

        verify(invoiceService).getUnpaidInvoices();
        verify(invoiceService, never()).getAllInvoices();
    }

    @Test
    public void createPayment_RejectsAmountAboveOutstandingBalance() throws Exception {
        Invoice invoice = new Invoice();
        invoice.setTotalAmount(new BigDecimal("100.00"));
        invoice.setPaidAmount(new BigDecimal("80.00"));
        when(invoiceService.getInvoiceById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceService.getOutstandingAmount(invoice)).thenReturn(new BigDecimal("20.00"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/payments")
                        .param("invoiceId", "1")
                        .param("amount", "30.00")
                        .param("paymentDate", "2026-09-30")
                        .param("paymentMethod", "CASH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payments/new"))
                .andExpect(flash().attributeExists("flashError"));

        verify(paymentService, never()).recordPayment(any(Payment.class));
    }

    @Test
    public void createPayment_UsesSelectedPaymentDate() throws Exception {
        Invoice invoice = new Invoice();
        invoice.setId(1L);
        when(invoiceService.getInvoiceById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceService.getOutstandingAmount(invoice)).thenReturn(new BigDecimal("100.00"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/payments")
                        .param("invoiceId", "1")
                        .param("amount", "30.00")
                        .param("paymentDate", "2026-09-30")
                        .param("paymentMethod", "CASH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/1"));

        org.mockito.ArgumentCaptor<Payment> paymentCaptor =
                org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentService).recordPayment(paymentCaptor.capture());
        assertEquals(LocalDate.of(2026, 9, 30), paymentCaptor.getValue().getPaymentDate());
    }

    @Test
    public void getCustomerDetails_ReturnsJson() throws Exception {
        CustomerPaymentDetailsDTO dto = new CustomerPaymentDetailsDTO(1L, "Test Corp", new BigDecimal("500.00"), new java.util.ArrayList<>());
        
        when(customerService.getPaymentDetails(anyLong())).thenReturn(dto);

        mockMvc.perform(get("/payments/customer/1/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1L))
                .andExpect(jsonPath("$.customerName").value("Test Corp"))
                .andExpect(jsonPath("$.totalBalance").value(500.00));
    }

    @Test
    public void createCustomerPayment_Success() throws Exception {
        ChartOfAccount bank = new ChartOfAccount(); bank.setId(1L);
        ChartOfAccount ar = new ChartOfAccount(); ar.setId(2L);
        when(accountService.getAccountById(1L)).thenReturn(Optional.of(bank));
        when(accountService.getAccountById(2L)).thenReturn(Optional.of(ar));
        when(invoiceService.getInvoiceById(10L)).thenReturn(Optional.of(new Invoice()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/payments/customer")
                .param("customerId", "1")
                .param("bankAccountId", "1")
                .param("arAccountId", "2")
                .param("paymentDate", "2023-10-01")
                .param("paymentMethod", "CHEQUE")
                .param("invoiceIds", "10")
                .param("invoiceAmounts", "100.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payments"))
                .andExpect(request().sessionAttribute("lastBankAccountId", 1L))
                .andExpect(request().sessionAttribute("lastArAccountId", 2L));
    }
}
