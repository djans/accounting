package com.cogitosum.service;

import com.cogitosum.entity.Customer;
import com.cogitosum.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {
    
    @Autowired
    private CustomerRepository customerRepository;
    
    public Customer createCustomer(Customer customer) {
        return customerRepository.save(customer);
    }
    
    public Customer updateCustomer(Long id, Customer customer) {
        Optional<Customer> existingCustomer = customerRepository.findById(id);
        if (existingCustomer.isPresent()) {
            Customer cust = existingCustomer.get();
            cust.setName(customer.getName());
            cust.setEmail(customer.getEmail());
            cust.setBusinessName(customer.getBusinessName());
            cust.setAddress(customer.getAddress());
            cust.setCity(customer.getCity());
            cust.setProvince(customer.getProvince());
            cust.setPostalCode(customer.getPostalCode());
            cust.setCountry(customer.getCountry());
            cust.setBusinessNumber(customer.getBusinessNumber());
            cust.setGstNumber(customer.getGstNumber());
            return customerRepository.save(cust);
        }
        return null;
    }
    
    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }
    
    public Optional<Customer> getCustomerByEmail(String email) {
        return customerRepository.findByEmail(email);
    }
    
    public Optional<Customer> getCustomerByGstNumber(String gstNumber) {
        return customerRepository.findByGstNumber(gstNumber);
    }
    
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }
    
    public void deleteCustomer(Long id) {
        customerRepository.deleteById(id);
    }
}

