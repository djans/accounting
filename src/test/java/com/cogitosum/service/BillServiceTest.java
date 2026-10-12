package com.cogitosum.service;

import com.cogitosum.entity.Bill;
import com.cogitosum.entity.BillStatus;
import com.cogitosum.repository.BillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillServiceTest {

    @Mock private BillRepository billRepository;
    @Mock private BillPostingService billPostingService;
    @Mock private CurrentCompanyContext companyContext;
    @InjectMocks private BillService billService;

    @BeforeEach
    void setUp() {
        when(companyContext.requireCompanyId()).thenReturn(1L);
    }

    @Test
    void receivedBillsCannotBeUpdatedOrDeleted() {
        Bill received = bill(BillStatus.RECEIVED);
        when(billRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(received));

        assertThrows(IllegalStateException.class, () -> billService.updateBill(10L, new Bill()));
        assertThrows(IllegalStateException.class, () -> billService.deleteBill(10L));

        verify(billRepository, never()).save(any());
        verify(billRepository, never()).delete(any());
    }

    @Test
    void cancellationRequiresAReasonAndRetainsItWithTheCancelledBill() {
        Bill received = bill(BillStatus.RECEIVED);
        when(billRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(received));
        when(billRepository.save(received)).thenReturn(received);

        assertThrows(IllegalArgumentException.class, () -> billService.cancelBill(10L, "  "));
        Bill cancelled = billService.cancelBill(10L, "  Duplicate entry  ");

        assertEquals(BillStatus.CANCELLED, cancelled.getStatus());
        assertEquals("Duplicate entry", cancelled.getCancellationReason());
        assertNotNull(cancelled.getCancelledAt());
        verify(billPostingService).reverseBill(received, "Bill cancelled: Duplicate entry");
    }

    @Test
    void partiallyPaidBillsMustHaveTheirPaymentsReversedBeforeCancellation() {
        Bill partiallyPaid = bill(BillStatus.PARTIALLY_PAID);
        partiallyPaid.setPaidAmount(new BigDecimal("25.00"));
        when(billRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(partiallyPaid));

        assertThrows(IllegalStateException.class,
                () -> billService.cancelBill(10L, "Incorrect vendor invoice"));

        verify(billRepository, never()).save(any());
    }

    private Bill bill(BillStatus status) {
        Bill bill = new Bill();
        bill.setId(10L);
        bill.setStatus(status);
        bill.setPaidAmount(BigDecimal.ZERO);
        return bill;
    }
}
