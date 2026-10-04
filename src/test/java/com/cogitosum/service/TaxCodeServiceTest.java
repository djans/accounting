package com.cogitosum.service;

import com.cogitosum.entity.TaxGroup;
import com.cogitosum.entity.TaxItem;
import com.cogitosum.entity.TaxAgency;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.TaxAgencyRepository;
import com.cogitosum.repository.TaxCodeRepository;
import com.cogitosum.repository.TaxGroupRepository;
import com.cogitosum.repository.TaxItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxCodeServiceTest {
    @Mock private TaxCodeRepository taxCodes;
    @Mock private TaxItemRepository taxItems;
    @Mock private TaxGroupRepository taxGroups;
    @Mock private TaxAgencyRepository taxAgencies;
    @Mock private ChartOfAccountRepository accounts;
    @Mock private CurrentCompanyContext companyContext;

    private TaxCodeService service;

    @BeforeEach
    void setUp() {
        service = new TaxCodeService();
        ReflectionTestUtils.setField(service, "taxCodeRepository", taxCodes);
        ReflectionTestUtils.setField(service, "taxItemRepository", taxItems);
        ReflectionTestUtils.setField(service, "taxGroupRepository", taxGroups);
        ReflectionTestUtils.setField(service, "taxAgencyRepository", taxAgencies);
        ReflectionTestUtils.setField(service, "accountRepository", accounts);
        ReflectionTestUtils.setField(service, "companyContext", companyContext);
        when(companyContext.requireCompanyId()).thenReturn(1L);
    }

    @Test
    void updateGroupReplacesComponentsUsingTheManagedCollection() {
        TaxItem originalItem = taxItem(10L);
        TaxItem replacementItem = taxItem(20L);
        TaxGroup existing = new TaxGroup();
        existing.setId(1L);
        existing.setTaxItems(new ArrayList<>(List.of(originalItem)));
        TaxGroup submitted = new TaxGroup();
        submitted.setCode("Achat QC");
        submitted.setName("Québec (TPS + TVQ)");
        submitted.setTaxItems(List.of(replacementItem));
        when(taxGroups.findByIdAndCompanyId(1L, 1L)).thenReturn(Optional.of(existing));
        when(taxItems.findByIdAndCompanyId(20L, 1L)).thenReturn(Optional.of(replacementItem));
        when(taxGroups.save(existing)).thenReturn(existing);

        TaxGroup updated = service.updateGroup(1L, submitted);

        assertEquals("Achat QC", updated.getCode());
        assertEquals(List.of(replacementItem), updated.getTaxItems());
    }

    @Test
    void updateItemPersistsSeparateSalesAndPurchaseReturnLines() {
        TaxAgency agency = new TaxAgency();
        agency.setId(5L);
        TaxItem existing = taxItem(10L);
        existing.setAgency(agency);
        TaxItem submitted = new TaxItem();
        submitted.setCode("GST");
        submitted.setName("GST");
        submitted.setRate(new java.math.BigDecimal("0.05000"));
        submitted.setAgency(agency);
        submitted.setForSales(true);
        submitted.setForPurchases(true);
        submitted.setSalesReturnLine("103");
        submitted.setPurchaseReturnLine("106");
        when(taxItems.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(existing));
        when(taxAgencies.findByIdAndCompanyId(5L, 1L)).thenReturn(Optional.of(agency));
        when(taxItems.save(any(TaxItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxItem updated = service.updateItem(10L, submitted);

        assertEquals("103", updated.getSalesReturnLine());
        assertEquals("106", updated.getPurchaseReturnLine());
        verify(taxItems).save(existing);
    }

    private TaxItem taxItem(Long id) {
        TaxItem item = new TaxItem();
        item.setId(id);
        return item;
    }
}
