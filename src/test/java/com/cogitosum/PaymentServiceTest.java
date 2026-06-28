package com.cogitosum;

import com.cogitosum.entity.*;
import com.cogitosum.repository.PaymentRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@SpringBootTest
@Transactional
public class PaymentServiceTest {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private com.cogitosum.service.PaymentService paymentService;
    @Autowired private com.cogitosum.repository.CustomerRepository customerRepository;
    @Autowired private com.cogitosum.repository.InvoiceRepository invoiceRepository;

    @Test
    public void saveCheque_and_fetchByPaymentMethod() {
        // create customer
        Customer c = new Customer();
        c.setName("Test Cust");
        c.setEmail("test@example.com");
        c.setBusinessName("Test Co");
        c.setAddress("123 Test St");
        c.setCity("Testville");
        c.setProvince("QC");
        c.setPostalCode("A1A1A1");
        c.setCountry("CA");
        customerRepository.save(c);

        // create invoice minimal
        Invoice inv = new Invoice();
        inv.setInvoiceNumber("INV-001");
        inv.setCustomer(c);
        inv.setInvoiceDate(LocalDate.now());
        inv.setDueDate(LocalDate.now());
        inv.setStatus(InvoiceStatus.DRAFT);
        inv.setSubtotal(BigDecimal.ZERO);
        inv.setGstAmount(BigDecimal.ZERO);
        inv.setHstAmount(BigDecimal.ZERO);
        inv.setQstAmount(BigDecimal.ZERO);
        inv.setTotalAmount(new BigDecimal("50.00"));
        invoiceRepository.save(inv);

        Payment p = new Payment();
        p.setInvoice(inv);
        p.setAmount(new BigDecimal("50.00"));
        p.setPaymentDate(LocalDate.now());
        p.setPaymentMethod(PaymentMethod.CHEQUE);
        p.setStatus(PaymentStatus.PENDING);
        p.setTransactionId("TX-1");
        paymentRepository.save(p);

        var cheques = paymentService.getPaymentsByMethod(PaymentMethod.CHEQUE);
        Assertions.assertFalse(cheques.isEmpty());
        boolean found = cheques.stream().anyMatch(x -> "TX-1".equals(x.getTransactionId()));
        Assertions.assertTrue(found, "Saved cheque payment should be returned by service");
    }
}
