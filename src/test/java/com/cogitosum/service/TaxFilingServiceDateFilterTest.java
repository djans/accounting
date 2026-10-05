package com.cogitosum.service;

import com.cogitosum.entity.TaxFilingPeriod;
import com.cogitosum.entity.TaxFilingStatus;
import com.cogitosum.entity.TaxAgency;
import com.cogitosum.repository.TaxFilingPeriodRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaxFilingServiceDateFilterTest {

    @Test
    void usesLatestUnfiledAndMostRecentEarlierFiledTaxPeriods() {
        TaxFilingPeriodRepository repository = mock(TaxFilingPeriodRepository.class);
        CurrentCompanyContext companyContext = mock(CurrentCompanyContext.class);
        when(companyContext.requireCompanyId()).thenReturn(9L);
        when(repository.findAllByCompanyId(9L)).thenReturn(List.of(
                period("2026-04-01", "2026-06-30", TaxFilingStatus.PAID),
                period("2026-07-01", "2026-09-30", TaxFilingStatus.FILED),
                period("RQ", "2026-09-01", "2026-09-30", TaxFilingStatus.FILED),
                period("2026-10-01", "2026-12-31", TaxFilingStatus.CALCULATED),
                period("2027-01-01", "2027-03-31", TaxFilingStatus.OPEN)));
        TaxFilingService service = service(repository, companyContext);

        TaxFilingService.FilterPeriodRanges ranges =
                service.getDateFilterPeriods(LocalDate.of(2026, 10, 4));

        assertEquals(new TaxFilingService.DateRange("CRA",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)), ranges.current());
        assertEquals(new TaxFilingService.DateRange("CRA",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)), ranges.previous());
    }

    @Test
    void doesNotTreatFiledPeriodsAsCurrentWhenNoUnfiledPeriodExists() {
        TaxFilingPeriodRepository repository = mock(TaxFilingPeriodRepository.class);
        CurrentCompanyContext companyContext = mock(CurrentCompanyContext.class);
        when(companyContext.requireCompanyId()).thenReturn(9L);
        when(repository.findAllByCompanyId(9L)).thenReturn(List.of(
                period("2026-04-01", "2026-06-30", TaxFilingStatus.PAID),
                period("2026-07-01", "2026-09-30", TaxFilingStatus.FILED)));
        TaxFilingService service = service(repository, companyContext);

        TaxFilingService.FilterPeriodRanges ranges =
                service.getDateFilterPeriods(LocalDate.of(2026, 10, 4));

        assertNull(ranges.current());
        assertEquals(new TaxFilingService.DateRange("CRA",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)), ranges.previous());
    }

    private TaxFilingService service(TaxFilingPeriodRepository repository,
                                    CurrentCompanyContext companyContext) {
        TaxFilingService service = new TaxFilingService();
        ReflectionTestUtils.setField(service, "periodRepository", repository);
        ReflectionTestUtils.setField(service, "companyContext", companyContext);
        return service;
    }

    private TaxFilingPeriod period(String start, String end, TaxFilingStatus status) {
        return period("CRA", start, end, status);
    }

    private TaxFilingPeriod period(String agencyCode, String start, String end, TaxFilingStatus status) {
        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setPeriodStart(LocalDate.parse(start));
        period.setPeriodEnd(LocalDate.parse(end));
        period.setStatus(status);
        TaxAgency agency = new TaxAgency();
        agency.setId("CRA".equals(agencyCode) ? 1L : 2L);
        agency.setCode(agencyCode);
        period.setAgency(agency);
        return period;
    }
}
