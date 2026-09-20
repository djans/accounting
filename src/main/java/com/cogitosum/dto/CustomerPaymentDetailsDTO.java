package com.cogitosum.dto;

import com.cogitosum.dto.InvoiceDTO;
import java.math.BigDecimal;
import java.util.List;

public class CustomerPaymentDetailsDTO {
    private Long customerId;
    private String customerName;
    private BigDecimal totalBalance;
    private List<InvoiceDTO> openInvoices;

    public CustomerPaymentDetailsDTO(Long customerId, String customerName, BigDecimal totalBalance, List<InvoiceDTO> openInvoices) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.totalBalance = totalBalance;
        this.openInvoices = openInvoices;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public List<InvoiceDTO> getOpenInvoices() {
        return openInvoices;
    }
}
