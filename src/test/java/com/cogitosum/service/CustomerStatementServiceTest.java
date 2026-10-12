package com.cogitosum.service;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteRefund;
import com.cogitosum.entity.CreditNoteAllocation;
import com.cogitosum.entity.CreditNoteStatus;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.Payment;
import com.cogitosum.entity.PaymentStatus;
import com.cogitosum.repository.CustomerRepository;
import com.cogitosum.repository.CreditNoteRepository;
import com.cogitosum.repository.CreditNoteRefundRepository;
import com.cogitosum.repository.CreditNoteAllocationRepository;
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
    void recordsCreditNoteRefundAsASeparateCustomerAccountDebit() {
        CustomerRepository customers = mock(CustomerRepository.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        CreditNoteRepository creditNotes = mock(CreditNoteRepository.class);
        CreditNoteRefundRepository refunds = mock(CreditNoteRefundRepository.class);
        CreditNoteAllocationRepository allocations = mock(CreditNoteAllocationRepository.class);
        CurrentCompanyContext context = mock(CurrentCompanyContext.class);
        Customer customer = new Customer();
        customer.setId(2L);
        Invoice invoice = invoice(customer, "INV-300", LocalDate.of(2026, 2, 1), "100.00");
        CreditNote note = new CreditNote();
        note.setId(3L);
        note.setInvoice(invoice);
        note.setCreditNoteNumber("CN-003");
        note.setCreditNoteDate(LocalDate.of(2026, 2, 10));
        note.setStatus(CreditNoteStatus.ISSUED);
        note.setTotalAmount(new BigDecimal("25.00"));
        CreditNoteRefund refund = new CreditNoteRefund();
        refund.setId(4L);
        refund.setCreditNote(note);
        refund.setRefundDate(LocalDate.of(2026, 2, 15));
        refund.setAmount(new BigDecimal("25.00"));
        when(context.requireCompanyId()).thenReturn(7L);
        when(customers.findByIdAndCompanyId(2L, 7L)).thenReturn(Optional.of(customer));
        when(invoices.findByCompanyIdAndCustomerIdOrderByInvoiceNumberDesc(7L, 2L))
                .thenReturn(List.of(invoice));
        when(payments.findStatementPayments(7L, 2L))
                .thenReturn(List.of(payment(invoice, "TXN-100", LocalDate.of(2026, 2, 5),
                        "100.00", PaymentStatus.COMPLETED)));
        when(creditNotes.findByCompanyIdAndStatusAndCreditNoteDateLessThanEqualOrderByCreditNoteDateDescIdDesc(
                7L, CreditNoteStatus.ISSUED, LocalDate.of(2026, 2, 28))).thenReturn(List.of(note));
        when(refunds.findByCompanyIdAndRefundDateLessThanEqualOrderByRefundDate(
                7L, LocalDate.of(2026, 2, 28))).thenReturn(List.of(refund));

        CustomerStatementService.Statement statement = new CustomerStatementService(
                customers, invoices, payments, creditNotes, refunds, allocations, context)
                .getStatement(2L, null, LocalDate.of(2026, 2, 28));

        assertEquals(new BigDecimal("0.00"), statement.closingBalance());
        assertEquals("Refund for credit note CN-003", statement.lines().getLast().description());
        assertEquals(new BigDecimal("25.00"), statement.lines().getLast().debit());
    }

    @Test
    void recordsCreditAllocationWithoutChangingTheCustomerStatementBalance() {
        CustomerRepository customers = mock(CustomerRepository.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        CreditNoteRepository creditNotes = mock(CreditNoteRepository.class);
        CreditNoteRefundRepository refunds = mock(CreditNoteRefundRepository.class);
        CreditNoteAllocationRepository allocations = mock(CreditNoteAllocationRepository.class);
        CurrentCompanyContext context = mock(CurrentCompanyContext.class);
        Customer customer = new Customer();
        customer.setId(2L);
        Invoice source = invoice(customer, "INV-310", LocalDate.of(2026, 2, 1), "100.00");
        source.setId(10L);
        Invoice target = invoice(customer, "INV-311", LocalDate.of(2026, 2, 12), "50.00");
        target.setId(11L);
        CreditNote note = new CreditNote();
        note.setId(3L);
        note.setInvoice(source);
        note.setCreditNoteNumber("CN-010");
        note.setCreditNoteDate(LocalDate.of(2026, 2, 10));
        note.setStatus(CreditNoteStatus.ISSUED);
        note.setTotalAmount(new BigDecimal("25.00"));
        CreditNoteAllocation allocation = new CreditNoteAllocation();
        allocation.setId(5L);
        allocation.setCreditNote(note);
        allocation.setTargetInvoice(target);
        allocation.setAllocationDate(LocalDate.of(2026, 2, 15));
        allocation.setAmount(new BigDecimal("25.00"));
        when(context.requireCompanyId()).thenReturn(7L);
        when(customers.findByIdAndCompanyId(2L, 7L)).thenReturn(Optional.of(customer));
        when(invoices.findByCompanyIdAndCustomerIdOrderByInvoiceNumberDesc(7L, 2L))
                .thenReturn(List.of(source, target));
        when(payments.findStatementPayments(7L, 2L))
                .thenReturn(List.of(payment(source, "TXN-310", LocalDate.of(2026, 2, 5),
                        "100.00", PaymentStatus.COMPLETED)));
        when(creditNotes.findByCompanyIdAndStatusAndCreditNoteDateLessThanEqualOrderByCreditNoteDateDescIdDesc(
                7L, CreditNoteStatus.ISSUED, LocalDate.of(2026, 2, 28))).thenReturn(List.of(note));
        when(allocations.findByCompanyIdAndAllocationDateLessThanEqualOrderByAllocationDate(
                7L, LocalDate.of(2026, 2, 28))).thenReturn(List.of(allocation));

        CustomerStatementService.Statement statement = new CustomerStatementService(
                customers, invoices, payments, creditNotes, refunds, allocations, context)
                .getStatement(2L, null, LocalDate.of(2026, 2, 28));

        assertEquals(new BigDecimal("25.00"), statement.closingBalance());
        assertEquals("Credit note CN-010 applied to invoice INV-311",
                statement.lines().getLast().description());
        assertEquals(BigDecimal.ZERO, statement.lines().getLast().debit());
        assertEquals(BigDecimal.ZERO, statement.lines().getLast().credit());
    }

    @Test
    void includesCreditNotesEvenWhenTheCustomerHasNoPayments() {
        CustomerRepository customers = mock(CustomerRepository.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        CreditNoteRepository creditNotes = mock(CreditNoteRepository.class);
        CreditNoteRefundRepository creditNoteRefunds = mock(CreditNoteRefundRepository.class);
        CreditNoteAllocationRepository creditNoteAllocations = mock(CreditNoteAllocationRepository.class);
        CurrentCompanyContext context = mock(CurrentCompanyContext.class);
        Customer customer = new Customer();
        customer.setId(2L);
        Invoice invoice = invoice(customer, "INV-200", LocalDate.of(2026, 2, 1), "100.00");
        CreditNote note = new CreditNote();
        note.setInvoice(invoice);
        note.setCreditNoteNumber("CN-001");
        note.setCreditNoteDate(LocalDate.of(2026, 2, 15));
        note.setStatus(CreditNoteStatus.ISSUED);
        note.setTotalAmount(new BigDecimal("25.00"));
        when(context.requireCompanyId()).thenReturn(7L);
        when(customers.findByIdAndCompanyId(2L, 7L)).thenReturn(Optional.of(customer));
        when(invoices.findByCompanyIdAndCustomerIdOrderByInvoiceNumberDesc(7L, 2L))
                .thenReturn(List.of(invoice));
        when(creditNotes.findByCompanyIdAndStatusAndCreditNoteDateLessThanEqualOrderByCreditNoteDateDescIdDesc(
                7L, CreditNoteStatus.ISSUED, LocalDate.of(2026, 2, 28))).thenReturn(List.of(note));
        when(payments.findStatementPayments(7L, 2L)).thenReturn(List.of());

        CustomerStatementService.Statement statement = new CustomerStatementService(
                customers, invoices, payments, creditNotes, creditNoteRefunds, creditNoteAllocations, context)
                .getStatement(2L, null, LocalDate.of(2026, 2, 28));

        assertEquals(new BigDecimal("75.00"), statement.closingBalance());
        assertEquals(2, statement.lines().size());
        assertEquals(new BigDecimal("25.00"), statement.lines().getLast().credit());
    }

    @Test
    void calculatesOpeningAndClosingBalancesForTheSelectedPeriod() {
        CustomerRepository customers = mock(CustomerRepository.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        CreditNoteRepository creditNotes = mock(CreditNoteRepository.class);
        CreditNoteRefundRepository creditNoteRefunds = mock(CreditNoteRefundRepository.class);
        CreditNoteAllocationRepository creditNoteAllocations = mock(CreditNoteAllocationRepository.class);
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
                new CustomerStatementService(customers, invoices, payments, creditNotes, creditNoteRefunds,
                        creditNoteAllocations, context)
                        .getStatement(2L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28));

        assertEquals(new BigDecimal("100.00"), statement.openingBalance());
        assertEquals(new BigDecimal("120.00"), statement.closingBalance());
        assertEquals(3, statement.lines().size());
        assertEquals(new BigDecimal("150.00"), statement.lines().getFirst().balance());
        assertEquals(new BigDecimal("120.00"), statement.lines().getLast().balance());

        CustomerStatementService.Statement refundPeriod =
                new CustomerStatementService(customers, invoices, payments, creditNotes, creditNoteRefunds,
                        creditNoteAllocations, context)
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
