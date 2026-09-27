package com.cogitosum.service;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {
    
    @Autowired
    private CustomerRepository customerRepository;
    
    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private CurrentCompanyContext companyContext;

    public Customer createCustomer(Customer customer) {
        Long companyId = companyContext.requireCompanyId();
        companyContext.assignCurrentCompany(customer);
        customer.setSalesTaxAccount(resolveAccount(customer.getSalesTaxAccount(), companyId));
        customer.setPurchaseTaxAccount(resolveAccount(customer.getPurchaseTaxAccount(), companyId));
        return customerRepository.save(customer);
    }
    
    public Customer updateCustomer(Long id, Customer customer) {
        Optional<Customer> existingCustomer = customerRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
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
            cust.setQstNumber(customer.getQstNumber());
            cust.setCompanyName(customer.getCompanyName());
            cust.setTitle(customer.getTitle());
            cust.setFirstName(customer.getFirstName());
            cust.setMiddleInitial(customer.getMiddleInitial());
            cust.setLastName(customer.getLastName());
            cust.setJobTitle(customer.getJobTitle());
            cust.setMainPhone(customer.getMainPhone());
            cust.setWorkPhone(customer.getWorkPhone());
            cust.setMobilePhone(customer.getMobilePhone());
            cust.setFax(customer.getFax());
            cust.setWebsite(customer.getWebsite());
            cust.setSecondaryEmail(customer.getSecondaryEmail());
            cust.setCcEmail(customer.getCcEmail());
            cust.setShipToAddress(customer.getShipToAddress());
            cust.setAccountNumber(customer.getAccountNumber());
            cust.setPaymentTerms(customer.getPaymentTerms());
            cust.setBillingRateLevel(customer.getBillingRateLevel());
            cust.setPrintOnChequeAs(customer.getPrintOnChequeAs());
            cust.setCreditLimit(customer.getCreditLimit());
            cust.setTaxReturnType(customer.getTaxReturnType());
            cust.setTaxReportingPeriod(customer.getTaxReportingPeriod());
            cust.setTaxPeriodEnding(customer.getTaxPeriodEnding());
            cust.setTaxLabel(customer.getTaxLabel());
            cust.setSalesTaxRegistrationNumber(customer.getSalesTaxRegistrationNumber());
            cust.setSalesTaxAccount(resolveAccount(customer.getSalesTaxAccount(), companyContext.requireCompanyId()));
            cust.setPurchaseTaxAccount(resolveAccount(customer.getPurchaseTaxAccount(), companyContext.requireCompanyId()));
            cust.setTrackSalesTaxSeparately(customer.isTrackSalesTaxSeparately());
            cust.setTrackPurchaseTaxSeparately(customer.isTrackPurchaseTaxSeparately());
            cust.setTaxOnOtherTaxes(customer.isTaxOnOtherTaxes());
            cust.setTaxIncludedOnSales(customer.isTaxIncludedOnSales());
            cust.setNotes(customer.getNotes());
            cust.setInactive(customer.isInactive());
            return customerRepository.save(cust);
        }
        return null;
    }

    private ChartOfAccount resolveAccount(ChartOfAccount account, Long companyId) {
        if (account == null) {
            return null;
        }
        if (account.getId() == null) {
            throw new IllegalArgumentException("Tax account is invalid");
        }
        return chartOfAccountRepository.findByIdAndCompanyId(account.getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Tax account not found"));
    }
    
    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }
    
    public Optional<Customer> getCustomerByEmail(String email) {
        return customerRepository.findByCompanyIdAndEmail(companyContext.requireCompanyId(), email);
    }
    
    public Optional<Customer> getCustomerByGstNumber(String gstNumber) {
        return customerRepository.findByCompanyIdAndGstNumber(companyContext.requireCompanyId(), gstNumber);
    }
    
    public List<Customer> getAllCustomers() {
        return customerRepository.findAllByCompanyIdOrderByBusinessNameAsc(companyContext.requireCompanyId());
    }
    
    public void deleteCustomer(Long id) {
        customerRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId())
                .ifPresent(customerRepository::delete);
    }

    public com.cogitosum.dto.CustomerPaymentDetailsDTO getPaymentDetails(Long customerId) {
        Customer customer = customerRepository.findByIdAndCompanyId(customerId, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        
        java.math.BigDecimal balance = invoiceService.getCustomerBalance(customerId);
        List<com.cogitosum.entity.Invoice> openInvoices = invoiceService.getUnpaidInvoicesByCustomerId(customerId);
        
        List<com.cogitosum.dto.InvoiceDTO> openInvoiceDTOs = openInvoices.stream()
                .map(inv -> {
                    com.cogitosum.dto.InvoiceDTO dto = new com.cogitosum.dto.InvoiceDTO();
                    dto.setId(inv.getId());
                    dto.setInvoiceNumber(inv.getInvoiceNumber());
                    dto.setInvoiceDate(inv.getInvoiceDate());
                    dto.setDueDate(inv.getDueDate());
                    dto.setStatus(inv.getStatus() != null ? inv.getStatus().name() : null);
                    dto.setTotalAmount(inv.getTotalAmount());
                    dto.setPaidAmount(inv.getPaidAmount());
                    return dto;
                })
                .toList();
        
        return new com.cogitosum.dto.CustomerPaymentDetailsDTO(
                customer.getId(),
                customer.getBusinessName(),
                balance,
                openInvoiceDTOs
        );
    }
}
