package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.Payment;
import com.cogitosum.entity.PaymentStatus;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentLifecycleServiceTest {

    @Mock private PaymentRepository payments;
    @Mock private InvoiceRepository invoices;
    @Mock private PaymentPostingService postings;
    @Mock private ChartOfAccountRepository accounts;
    @Mock private CurrentCompanyContext companyContext;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService();
        ReflectionTestUtils.setField(service, "paymentRepository", payments);
        ReflectionTestUtils.setField(service, "invoiceRepository", invoices);
        ReflectionTestUtils.setField(service, "paymentPostingService", postings);
        ReflectionTestUtils.setField(service, "accountRepository", accounts);
        ReflectionTestUtils.setField(service, "companyContext", companyContext);
        when(companyContext.requireCompanyId()).thenReturn(2L);
    }

    @Test
    void refundRecalculatesInvoiceBalanceAndReopensFullyUnpaidInvoice() {
        Invoice invoice = invoice("100.00", "100.00", InvoiceStatus.PAID);
        Payment payment = payment(invoice, "100.00", PaymentStatus.COMPLETED);
        when(payments.findByIdAndCompanyId(7L, 2L)).thenReturn(Optional.of(payment));
        when(payments.save(payment)).thenReturn(payment);
        when(payments.findByCompanyIdAndInvoiceId(2L, 10L)).thenReturn(List.of(payment));
        when(invoices.findByIdAndCompanyId(10L, 2L)).thenReturn(Optional.of(invoice));
        when(invoices.save(invoice)).thenReturn(invoice);

        Payment refunded = service.refundPayment(7L, "Payment journal was already reversed");

        assertEquals(PaymentStatus.REFUNDED, refunded.getStatus());
        assertEquals(BigDecimal.ZERO, invoice.getPaidAmount());
        assertEquals(InvoiceStatus.SENT, invoice.getStatus());
        verify(postings).reversePayment(payment, "Payment journal was already reversed");
    }

    @Test
    void completingPaymentRecalculatesFromPaymentsInsteadOfAddingItsAmountAgain() {
        Invoice invoice = invoice("100.00", "200.00", InvoiceStatus.PAID);
        Payment payment = payment(invoice, "100.00", PaymentStatus.PENDING);
        when(payments.findByIdAndCompanyId(7L, 2L)).thenReturn(Optional.of(payment));
        when(payments.save(payment)).thenReturn(payment);
        when(payments.findByCompanyIdAndInvoiceId(2L, 10L)).thenReturn(List.of(payment));
        when(invoices.findByIdAndCompanyId(10L, 2L)).thenReturn(Optional.of(invoice));
        when(invoices.save(invoice)).thenReturn(invoice);

        service.markPaymentAsCompleted(7L);

        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        assertEquals(new BigDecimal("100.00"), invoice.getPaidAmount());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void completingPaymentPreservesImportedOpeningPaidAmount() {
        Invoice invoice = invoice("100.00", "60.00", InvoiceStatus.PARTIALLY_PAID);
        invoice.setOpeningPaidAmount(new BigDecimal("60.00"));
        Payment payment = payment(invoice, "25.00", PaymentStatus.PENDING);
        when(payments.findByIdAndCompanyId(7L, 2L)).thenReturn(Optional.of(payment));
        when(payments.save(payment)).thenReturn(payment);
        when(payments.findByCompanyIdAndInvoiceId(2L, 10L)).thenReturn(List.of(payment));
        when(invoices.findByIdAndCompanyId(10L, 2L)).thenReturn(Optional.of(invoice));
        when(invoices.save(invoice)).thenReturn(invoice);

        service.markPaymentAsCompleted(7L);

        assertEquals(new BigDecimal("85.00"), invoice.getPaidAmount());
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
    }

    private Invoice invoice(String total, String paid, InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setId(10L);
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setPaidAmount(new BigDecimal(paid));
        invoice.setStatus(status);
        invoice.setDueDate(LocalDate.now().plusDays(30));
        return invoice;
    }

    private Payment payment(Invoice invoice, String amount, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(7L);
        payment.setInvoice(invoice);
        payment.setAmount(new BigDecimal(amount));
        payment.setStatus(status);
        payment.setTransactionId("TXN-7");
        return payment;
    }
}
