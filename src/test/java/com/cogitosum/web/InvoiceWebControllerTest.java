package com.cogitosum.web;

import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.service.CustomerService;
import com.cogitosum.service.InvoiceService;
import com.cogitosum.service.TaxFilingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvoiceWebControllerTest {

    private InvoiceService invoiceService;
    private CustomerService customerService;
    private TaxFilingService taxFilingService;
    private InvoiceWebController controller;

    @BeforeEach
    void setUp() {
        invoiceService = mock(InvoiceService.class);
        customerService = mock(CustomerService.class);
        taxFilingService = mock(TaxFilingService.class);
        when(taxFilingService.getDateFilterPeriods(any(LocalDate.class))).thenReturn(
                new TaxFilingService.FilterPeriodRanges(
                        new TaxFilingService.DateRange("CRA", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)),
                        new TaxFilingService.DateRange("CRA", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30))));
        controller = new InvoiceWebController();
        ReflectionTestUtils.setField(controller, "invoiceService", invoiceService);
        ReflectionTestUtils.setField(controller, "customerService", customerService);
        ReflectionTestUtils.setField(controller, "taxFilingService", taxFilingService);
    }

    @Test
    void listsAllInvoicesWhenNoStatusIsSelected() {
        when(invoiceService.getAllInvoices()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.list(null, model);

        assertEquals("invoices/list", view);
        assertEquals(InvoiceStatus.values().length, ((InvoiceStatus[]) model.get("invoiceStatuses")).length);
        assertNull(model.get("selectedStatus"));
        org.junit.jupiter.api.Assertions.assertNotNull(model.get("customers"));
        org.junit.jupiter.api.Assertions.assertNotNull(model.get("dateFilterPeriods"));
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
