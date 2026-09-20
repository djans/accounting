package com.cogitosum.web;

import com.cogitosum.entity.Customer;
import com.cogitosum.service.CustomerService;
import com.cogitosum.service.ChartOfAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerWebController.class)
public class CustomerWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private ChartOfAccountService accountService;

    @Test
    public void list_ReturnsView() throws Exception {
        when(customerService.getAllCustomers()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/customers"))
                .andExpect(status().isOk())
                .andExpect(view().name("customers/list"))
                .andExpect(model().attributeExists("customers"));
    }

    @Test
    public void newForm_ReturnsView() throws Exception {
        when(accountService.getAllAccounts()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/customers/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("customers/form"))
                .andExpect(model().attributeExists("customer", "accounts", "isNew"));
    }

    @Test
    public void editForm_ReturnsView() throws Exception {
        Customer c = new Customer();
        c.setId(1L);
        when(customerService.getCustomerById(1L)).thenReturn(Optional.of(c));
        when(accountService.getAllAccounts()).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/customers/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("customers/form"))
                .andExpect(model().attributeExists("customer", "accounts", "isNew"));
    }

    @Test
    public void create_RedirectsToList() throws Exception {
        when(customerService.createCustomer(any(Customer.class))).thenReturn(new Customer());
        mockMvc.perform(post("/customers")
                .param("businessName", "Acme Corp")
                .param("name", "John Doe")
                .param("email", "john@acme.com")
                .param("address", "123 Street")
                .param("city", "Montreal")
                .param("province", "QC")
                .param("postalCode", "H1H 1H1")
                .param("country", "Canada"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customers"));
    }
}
