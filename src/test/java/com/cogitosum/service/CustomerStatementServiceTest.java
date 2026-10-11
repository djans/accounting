package com.cogitosum.service;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.Payment;
import com.cogitosum.entity.PaymentStatus;
import com.cogitosum.repository.CustomerRepository;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerStatementServiceTest {

    @Test
    void calculatesOpeningAndClosingBalancesForTheSelectedPeriod() {
        CustomerRepository customers = mock(CustomerRepository.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        CurrentCompanyContext context = mock(CurrentCompanyContext.class);
        Customer customer = new Customer();
        customer.setId(2L);
        customer.setBusinessName("Example customer");
        Invoice broughtForward = invoice(customer, "INV-100", LocalDate.of(2025, 12, 15), "100.00");
        Invoice currentInvoice = invoice(customer, "INV-101", LocalDate.of(2026, 2, 1), "50.00");
        Invoice draft = invoice(customer, "INV-DRAFT", LocalDate.of(2026, 2, 2), "500.00");
        draft.setStatus(InvoiceStatus.DRAFT);
        when(context.requireCompanyId()).thenReturn(7L);
        when(customers.findByIdAndCompanyId(2L, 7L)).thenReturn(Optional.of(customer));
        when(invoices.findByCompanyIdAndCustomerIdOrderByInvoiceNumberDesc(7L, 2L))
                .thenReturn(List.of(broughtForward, currentInvoice, draft));
        Payment pendingPayment = payment(currentInvoice, "TXN-1", LocalDate.of(2026, 2, 5),
                "20.00", PaymentStatus.PENDING);
        Payment refundedPayment = payment(currentInvoice, "TXN-2", LocalDate.of(2026, 2, 8),
                "10.00", PaymentStatus.REFUNDED);
        refundedPayment.setUpdatedAt(LocalDateTime.of(2026, 3, 1, 9, 0));
        when(payments.findStatementPayments(7L, 2L))
                .thenReturn(List.of(pendingPayment, refundedPayment,
                        payment(currentInvoice, "TXN-3", LocalDate.of(2026, 2, 9),
                                "99.00", PaymentStatus.CANCELLED)));

        CustomerStatementService.Statement statement =
                new CustomerStatementService(customers, invoices, payments, context)
                        .getStatement(2L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28));

        assertEquals(new BigDecimal("100.00"), statement.openingBalance());
        assertEquals(new BigDecimal("120.00"), statement.closingBalance());
        assertEquals(3, statement.lines().size());
        assertEquals(new BigDecimal("150.00"), statement.lines().getFirst().balance());
        assertEquals(new BigDecimal("120.00"), statement.lines().getLast().balance());

        CustomerStatementService.Statement refundPeriod =
                new CustomerStatementService(customers, invoices, payments, context)
                        .getStatement(2L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertEquals(new BigDecimal("120.00"), refundPeriod.openingBalance());
        assertEquals(1, refundPeriod.lines().size());
        assertEquals("Reversal TXN-2", refundPeriod.lines().getFirst().description());
        assertEquals(new BigDecimal("130.00"), refundPeriod.closingBalance());
    }

    private Invoice invoice(Customer customer, String number, LocalDate date, String total) {
        Invoice invoice = new Invoice();
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber(number);
        invoice.setInvoiceDate(date);
        invoice.setDueDate(date.plusDays(30));
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setTotalAmount(new BigDecimal(total));
        return invoice;
    }

    private Payment payment(Invoice invoice, String id, LocalDate date, String amount, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setTransactionId(id);
        payment.setPaymentDate(date);
        payment.setAmount(new BigDecimal(amount));
        payment.setStatus(status);
        return payment;
    }
}
