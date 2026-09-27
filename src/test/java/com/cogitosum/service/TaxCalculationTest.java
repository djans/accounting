package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class TaxCalculationTest {

    @Mock
    private TaxCodeService taxCodeService;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void calculateInvoiceTotals_UsesTaxGroups() {
        // Setup Tax Items
        TaxItem tps = new TaxItem();
        tps.setCode("TPS");
        tps.setRate(new BigDecimal("0.05"));
        tps.setForSales(true);

        TaxItem tvq = new TaxItem();
        tvq.setCode("TVQ");
        tvq.setRate(new BigDecimal("0.10")); // 10% for easy math
        tvq.setForSales(true);

        // Setup Tax Group
        TaxGroup qcGroup = new TaxGroup();
        qcGroup.setCode("QC_GROUP");
        qcGroup.setTaxItems(List.of(tps, tvq));

        // Setup Tax Code (the high-level mapping)
        TaxCode qcTaxCode = new TaxCode();
        qcTaxCode.setCode("QC"); // This is what the invoice will look for
        qcTaxCode.setSalesTaxGroup(qcGroup);

        when(taxCodeService.getAllCodes()).thenReturn(List.of(qcTaxCode));

        // Setup Invoice
        Customer customer = new Customer();
        customer.setProvince("QC");

        Invoice invoice = new Invoice();
        invoice.setCustomer(customer);
        
        LineItem item = new LineItem();
        item.setQuantity(new BigDecimal("1"));
        item.setUnitPrice(new BigDecimal("100.00"));
        invoice.getLineItems().add(item);

        // Execute
        invoiceService.calculateInvoiceTotals(invoice);

        // Verify
        // Subtotal: 100.00
        // TPS (5%): 5.00
        // TVQ (10%): 10.00
        // Total: 115.00
        assertEquals(new BigDecimal("100.00"), invoice.getSubtotal());
        assertEquals(new BigDecimal("5.00"), invoice.getGstAmount());
        assertEquals(new BigDecimal("10.00"), invoice.getQstAmount());
        assertEquals(new BigDecimal("115.00"), invoice.getTotalAmount());
    }
}
