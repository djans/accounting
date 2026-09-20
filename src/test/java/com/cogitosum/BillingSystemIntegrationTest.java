package com.cogitosum;

import com.cogitosum.entity.Customer;
import com.cogitosum.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class BillingSystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        customerRepository.deleteAll();
    }

    @Test
    public void testCreateCustomer() throws Exception {
        Customer customer = new Customer();
        customer.setName("John Doe");
        customer.setEmail("john@example.com");
        customer.setBusinessName("Acme Corp");
        customer.setAddress("123 Main St");
        customer.setCity("Toronto");
        customer.setProvince("ON");
        customer.setPostalCode("M5H 2N2");
        customer.setCountry("Canada");
        customer.setGstNumber("123456789RT0001");

        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(customer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    public void testGetAllCustomers() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk());
    }

    @Test
    public void testGetCustomerByEmail() throws Exception {
        Customer customer = new Customer();
        customer.setName("Jane Doe");
        customer.setEmail("jane@example.com");
        customer.setBusinessName("Tech Corp");
        customer.setAddress("456 Oak Ave");
        customer.setCity("Vancouver");
        customer.setProvince("BC");
        customer.setPostalCode("V6B 1Y8");
        customer.setCountry("Canada");

        customerRepository.save(customer);

        mockMvc.perform(get("/api/customers/email/jane@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    public void testUpdateCustomer() throws Exception {
        Customer customer = new Customer();
        customer.setName("Original Name");
        customer.setEmail("test@example.com");
        customer.setBusinessName("Original Corp");
        customer.setAddress("100 Main St");
        customer.setCity("Montreal");
        customer.setProvince("QC");
        customer.setPostalCode("H2X 1Y5");
        customer.setCountry("Canada");

        Customer savedCustomer = customerRepository.save(customer);

        savedCustomer.setName("Updated Name");

        mockMvc.perform(put("/api/customers/" + savedCustomer.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(savedCustomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    public void testDeleteCustomer() throws Exception {
        Customer customer = new Customer();
        customer.setName("Delete Me");
        customer.setEmail("delete@example.com");
        customer.setBusinessName("Delete Corp");
        customer.setAddress("999 Delete St");
        customer.setCity("Calgary");
        customer.setProvince("AB");
        customer.setPostalCode("T2P 0R7");
        customer.setCountry("Canada");

        Customer savedCustomer = customerRepository.save(customer);

        mockMvc.perform(delete("/api/customers/" + savedCustomer.getId()))
                .andExpect(status().isNoContent());
    }
}
