package com.cogitosum.web;

import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.service.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class InvoiceWebControllerTest {

    private InvoiceService invoiceService;
    private InvoiceWebController controller;

    @BeforeEach
    void setUp() {
        invoiceService = mock(InvoiceService.class);
        controller = new InvoiceWebController();
        ReflectionTestUtils.setField(controller, "invoiceService", invoiceService);
    }

    @Test
    void listsAllInvoicesWhenNoStatusIsSelected() {
        when(invoiceService.getAllInvoices()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.list(null, model);

        assertEquals("invoices/list", view);
        assertEquals(InvoiceStatus.values().length, ((InvoiceStatus[]) model.get("invoiceStatuses")).length);
        assertNull(model.get("selectedStatus"));
        verify(invoiceService).getAllInvoices();
        verify(invoiceService, never()).getInvoicesByStatus(any());
    }

    @Test
    void filtersInvoicesBySelectedStatus() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.list(InvoiceStatus.PAID, model);

        assertEquals("invoices/list", view);
        assertEquals(InvoiceStatus.PAID, model.get("selectedStatus"));
        verify(invoiceService).getInvoicesByStatus(InvoiceStatus.PAID);
        verify(invoiceService, never()).getAllInvoices();
    }
}
