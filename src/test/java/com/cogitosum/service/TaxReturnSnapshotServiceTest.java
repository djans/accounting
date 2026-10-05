package com.cogitosum.service;

import com.cogitosum.dto.TaxReturnRowDTO;
import com.cogitosum.entity.Company;
import com.cogitosum.entity.TaxAgency;
import com.cogitosum.entity.TaxFilingPeriod;
import com.cogitosum.entity.TaxFilingStatus;
import com.cogitosum.entity.TaxReturnRowSnapshot;
import com.cogitosum.repository.BillRepository;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.TaxFilingPeriodRepository;
import com.cogitosum.repository.TaxReturnRowSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaxReturnSnapshotServiceTest {

    @Test
    void savesEveryComputedReturnRowInDisplayOrderWhileFiling() {
        TaxFilingPeriodRepository periods = mock(TaxFilingPeriodRepository.class);
        TaxReturnRowSnapshotRepository snapshots = mock(TaxReturnRowSnapshotRepository.class);
        CurrentCompanyContext companyContext = mock(CurrentCompanyContext.class);
        GeneralJournalService journals = mock(GeneralJournalService.class);
        BillRepository bills = mock(BillRepository.class);
        InvoiceRepository invoices = mock(InvoiceRepository.class);
        TaxFilingPeriod period = period(TaxFilingStatus.OPEN);
        when(companyContext.requireCompanyId()).thenReturn(7L);
        when(periods.findByIdAndCompanyId(12L, 7L)).thenReturn(Optional.of(period));
        when(periods.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bills.findByCompanyIdAndBillDateBetweenOrderByBillDateDesc(eq(7L), any(), any()))
                .thenReturn(List.of());
        when(journals.getJournalsByDateRange(any(), any())).thenReturn(List.of());

        TaxReturnRowDTO sales = new TaxReturnRowDTO(
                "tax.detail.returnLine101", "101", new BigDecimal("500.00"), null, false, false);
        TaxReturnRowDTO total = new TaxReturnRowDTO(
                "tax.detail.returnLine105", "105", null, new BigDecimal("25.00"), true, false);
        TaxFilingService service = spy(new TaxFilingService());
        ReflectionTestUtils.setField(service, "periodRepository", periods);
        ReflectionTestUtils.setField(service, "returnRowSnapshotRepository", snapshots);
        ReflectionTestUtils.setField(service, "companyContext", companyContext);
        ReflectionTestUtils.setField(service, "journalService", journals);
        ReflectionTestUtils.setField(service, "billRepository", bills);
        ReflectionTestUtils.setField(service, "invoiceRepository", invoices);
        doReturn(List.of()).when(service).getReturnLineBreakdown(period);
        doReturn(false).when(service).hasUnmappedReturnLineAmounts(period, List.of());
        doReturn(List.of(sales, total)).when(service).getTaxReturnRows(period, List.of());

        service.file(12L, "test");

        var rowsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(snapshots).saveAll(rowsCaptor.capture());
        @SuppressWarnings("unchecked")
        List<TaxReturnRowSnapshot> saved = rowsCaptor.getValue();
        assertEquals(2, saved.size());
        assertEquals(0, saved.get(0).getDisplayOrder());
        assertEquals("101", saved.get(0).getLine());
        assertEquals(new BigDecimal("500.00"), saved.get(0).getAmount());
        assertEquals(1, saved.get(1).getDisplayOrder());
        assertTrue(saved.get(1).isTotal());
        assertEquals(new BigDecimal("25.00"), saved.get(1).getBalance());
        assertEquals(TaxFilingStatus.FILED, period.getStatus());
    }

    @Test
    void reconstructsOldFiledPeriodButDoesNotOfferAnOpenPeriod() {
        TaxFilingPeriodRepository periods = mock(TaxFilingPeriodRepository.class);
        TaxReturnRowSnapshotRepository snapshots = mock(TaxReturnRowSnapshotRepository.class);
        CurrentCompanyContext companyContext = mock(CurrentCompanyContext.class);
        when(companyContext.requireCompanyId()).thenReturn(7L);
        TaxFilingPeriod filed = period(TaxFilingStatus.FILED);
        TaxFilingPeriod open = period(TaxFilingStatus.OPEN);
        when(periods.findByIdAndCompanyId(12L, 7L)).thenReturn(Optional.of(filed));
        when(periods.findByIdAndCompanyId(13L, 7L)).thenReturn(Optional.of(open));
        when(snapshots.findByPeriodIdOrderByDisplayOrderAsc(12L)).thenReturn(List.of());
        TaxReturnRowDTO currentRow = new TaxReturnRowDTO(
                "tax.detail.returnLine101", "101", BigDecimal.ZERO, null, false, false);

        TaxFilingService service = spy(new TaxFilingService());
        ReflectionTestUtils.setField(service, "periodRepository", periods);
        ReflectionTestUtils.setField(service, "returnRowSnapshotRepository", snapshots);
        ReflectionTestUtils.setField(service, "companyContext", companyContext);
        doReturn(List.of()).when(service).getReturnLineBreakdown(filed);
        doReturn(List.of(currentRow)).when(service).getTaxReturnRows(filed, List.of());

        var report = service.getFiledReturnReport(12L).orElseThrow();
        assertTrue(report.reconstructed());
        assertEquals(List.of(currentRow), report.rows());
        assertEquals("Company Ltd.", report.companyName());
        assertTrue(service.getFiledReturnReport(13L).isEmpty());
        verify(periods).findByIdAndCompanyId(12L, 7L);
        verify(periods).findByIdAndCompanyId(13L, 7L);
    }

    @Test
    void usesSavedRowsWithoutRecomputingCurrentTransactions() {
        TaxFilingPeriodRepository periods = mock(TaxFilingPeriodRepository.class);
        TaxReturnRowSnapshotRepository snapshots = mock(TaxReturnRowSnapshotRepository.class);
        CurrentCompanyContext companyContext = mock(CurrentCompanyContext.class);
        when(companyContext.requireCompanyId()).thenReturn(7L);
        TaxFilingPeriod period = period(TaxFilingStatus.PAID);
        when(periods.findByIdAndCompanyId(12L, 7L)).thenReturn(Optional.of(period));
        TaxReturnRowSnapshot saved = new TaxReturnRowSnapshot();
        saved.setPeriod(period);
        saved.setDisplayOrder(0);
        saved.setDescriptionKey("tax.detail.returnLine101");
        saved.setLine("101");
        saved.setAmount(new BigDecimal("987.65"));
        when(snapshots.findByPeriodIdOrderByDisplayOrderAsc(12L)).thenReturn(List.of(saved));

        TaxFilingService service = spy(new TaxFilingService());
        ReflectionTestUtils.setField(service, "periodRepository", periods);
        ReflectionTestUtils.setField(service, "returnRowSnapshotRepository", snapshots);
        ReflectionTestUtils.setField(service, "companyContext", companyContext);

        var report = service.getFiledReturnReport(12L).orElseThrow();

        assertFalse(report.reconstructed());
        assertEquals(new BigDecimal("987.65"), report.rows().get(0).amount());
        verify(service, never()).getReturnLineBreakdown(any());
    }

    private TaxFilingPeriod period(TaxFilingStatus status) {
        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setId(12L);
        period.setStatus(status);
        period.setPeriodStart(LocalDate.of(2026, 7, 1));
        period.setPeriodEnd(LocalDate.of(2026, 9, 30));
        TaxAgency agency = new TaxAgency();
        agency.setCode(TaxFilingService.CRA_CODE);
        agency.setName("CRA");
        period.setAgency(agency);
        Company company = new Company();
        company.setName("Company");
        company.setLegalName("Company Ltd.");
        period.setCompany(company);
        return period;
    }
}
