package com.cogitosum.web;

import com.cogitosum.dto.CustomerPaymentDetailsDTO;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Invoice;
import com.cogitosum.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
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

    @Test
    public void customerPaymentForm_ReturnsView() throws Exception {
        mockMvc.perform(get("/payments/customer"))
                .andExpect(status().isOk())
                .andExpect(view().name("payments/customer_payment"))
                .andExpect(model().attributeExists("customers", "methods", "bankAccounts", "arAccounts"))
                .andExpect(model().attributeExists("defaultBankAccountId", "defaultArAccountId"));
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
