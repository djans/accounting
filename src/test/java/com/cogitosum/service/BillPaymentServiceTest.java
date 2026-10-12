package com.cogitosum.service;

import com.cogitosum.entity.Bill;
import com.cogitosum.entity.BillPayment;
import com.cogitosum.entity.BillStatus;
import com.cogitosum.entity.Company;
import com.cogitosum.entity.PaymentMethod;
import com.cogitosum.entity.PaymentStatus;
import com.cogitosum.repository.BillPaymentRepository;
import com.cogitosum.repository.BillRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillPaymentServiceTest {

    @Mock private BillPaymentRepository paymentRepository;
    @Mock private BillRepository billRepository;
    @Mock private BillPaymentPostingService postingService;
    @Mock private ChartOfAccountRepository accountRepository;
    @Mock private CurrentCompanyContext companyContext;

    private final Bill bill = bill();
    private BillPaymentService service;

    @BeforeEach
    void setUp() {
        service = new BillPaymentService(paymentRepository, billRepository, postingService,
                accountRepository, companyContext);
    }

    @Test
    void recordingAndCompletingPaymentOnlyChangeThePaidTotalOnce() {
        BillPayment payment = payment("40.00");
        payment.setId(20L);
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(companyContext.requireCompany()).thenReturn(new Company());
        when(billRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(bill));
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(BillPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByIdAndCompanyId(20L, 1L)).thenReturn(Optional.of(payment));

        BillPayment saved = service.recordPayment(payment);
        assertEquals(PaymentStatus.PENDING, saved.getStatus());
        assertEquals(new BigDecimal("40.00"), bill.getPaidAmount());
        assertEquals(BillStatus.PARTIALLY_PAID, bill.getStatus());

        service.markPaymentAsCompleted(20L);
        service.markPaymentAsCompleted(20L);

        assertEquals(new BigDecimal("40.00"), bill.getPaidAmount());
        assertEquals(BillStatus.PARTIALLY_PAID, bill.getStatus());
        verify(billRepository).save(bill);
    }

    @Test
    void refundReversesPaidTotalOnceAndRetainsThePaymentRecord() {
        BillPayment payment = payment("40.00");
        payment.setId(20L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setBill(bill);
        bill.setPaidAmount(new BigDecimal("40.00"));
        bill.setStatus(BillStatus.PARTIALLY_PAID);
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(paymentRepository.findByIdAndCompanyId(20L, 1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(billRepository.save(bill)).thenReturn(bill);

        service.refundPayment(20L);

        assertEquals(new BigDecimal("0.00"), bill.getPaidAmount());
        assertEquals(BillStatus.RECEIVED, bill.getStatus());
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(paymentRepository, never()).delete(any());
        assertThrows(IllegalStateException.class, () -> service.refundPayment(20L));
    }

    @Test
    void paymentsCannotBeRecordedAgainstDraftBills() {
        bill.setStatus(BillStatus.DRAFT);
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(billRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(bill));

        assertThrows(IllegalStateException.class, () -> service.recordPayment(payment("10.00")));

        verify(paymentRepository, never()).save(any());
    }

    private BillPayment payment(String amount) {
        BillPayment payment = new BillPayment();
        Bill reference = new Bill();
        reference.setId(10L);
        payment.setBill(reference);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentMethod(PaymentMethod.CASH);
        return payment;
    }

    private static Bill bill() {
        Bill bill = new Bill();
        bill.setId(10L);
        bill.setStatus(BillStatus.RECEIVED);
        bill.setTotalAmount(new BigDecimal("100.00"));
        bill.setPaidAmount(BigDecimal.ZERO);
        return bill;
    }
}
