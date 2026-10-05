package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock CompanyRepository companies;
    @Mock CompanyMembershipRepository memberships;
    @Mock CurrentCompanyContext context;
    @Mock ChartOfAccountRepository accounts;
    @Mock TaxAgencyRepository agencies;
    @Mock TaxItemRepository items;
    @Mock TaxGroupRepository groups;
    @Mock TaxCodeRepository codes;

    private CompanyService service() {
        return new CompanyService(companies, memberships, context, accounts, agencies, items, groups, codes);
    }

    @Test
    void clonesCompanySetupWithoutOpeningBalancesOrActivity() {
        Company source = company(10L, "Source");
        source.setCurrency("USD");
        source.setDefaultTaxProvince("QC");
        source.setFiscalYearStartMonth(4);
        Company created = new Company();
        created.setName("Destination");
        created.setLegalName("Destination Inc.");
        created.setEmail("destination@example.test");
        created.setPhone("555-0100");
        created.setAddress("1 Main Street");
        created.setCity("Montréal");
        created.setProvince("QC");
        created.setPostalCode("H0H 0H0");
        created.setCountry("Canada");
        created.setPostedJournalEditingEnabled(true);
        UserAccount user = new UserAccount();
        user.setId(4L);
        when(context.currentUser()).thenReturn(user);
        when(context.canAccessCompany(10L)).thenReturn(true);
        when(companies.findByEmailIgnoreCase("destination@example.test")).thenReturn(Optional.empty());
        when(companies.findById(10L)).thenReturn(Optional.of(source));
        when(companies.save(created)).thenAnswer(invocation -> {
            created.setId(20L);
            return created;
        });
        when(accounts.findAllByCompanyIdOrderByAccountNumberAsc(10L)).thenReturn(List.of(account(1L, source)));
        when(accounts.save(any(ChartOfAccount.class))).thenAnswer(invocation -> {
            ChartOfAccount copy = invocation.getArgument(0);
            copy.setId(101L);
            return copy;
        });
        when(accounts.findAllByCompanyIdOrderByAccountNumberAsc(20L))
            .thenReturn(List.of(account(101L, created)));
        TaxAgency sourceAgency = new TaxAgency();
        sourceAgency.setId(30L);
        sourceAgency.setCompany(source);
        sourceAgency.setCode("GST");
        sourceAgency.setName("GST Agency");
        TaxItem sourceItem = new TaxItem();
        sourceItem.setId(40L);
        sourceItem.setCompany(source);
        sourceItem.setCode("GST5");
        sourceItem.setName("GST 5%");
        sourceItem.setRate(new BigDecimal("0.05000"));
        sourceItem.setAgency(sourceAgency);
        sourceItem.setPayableAccount(account(1L, source));
        TaxGroup sourceGroup = new TaxGroup();
        sourceGroup.setId(50L);
        sourceGroup.setCompany(source);
        sourceGroup.setCode("GST_GROUP");
        sourceGroup.setName("GST group");
        sourceGroup.setTaxItems(new ArrayList<>(List.of(sourceItem)));
        TaxCode sourceCode = new TaxCode();
        sourceCode.setId(60L);
        sourceCode.setCompany(source);
        sourceCode.setCode("GST_CODE");
        sourceCode.setName("GST");
        sourceCode.setSalesTaxGroup(sourceGroup);
        sourceCode.setPurchaseTaxGroup(sourceGroup);
        when(agencies.findAllByCompanyIdOrderByCodeAsc(10L)).thenReturn(List.of(sourceAgency));
        when(agencies.save(any(TaxAgency.class))).thenAnswer(invocation -> {
            TaxAgency copy = invocation.getArgument(0);
            copy.setId(130L);
            return copy;
        });
        when(items.findAllByCompanyIdOrderByCodeAsc(10L)).thenReturn(List.of(sourceItem));
        when(items.save(any(TaxItem.class))).thenAnswer(invocation -> {
            TaxItem copy = invocation.getArgument(0);
            copy.setId(140L);
            return copy;
        });
        when(groups.findAllByCompanyIdOrderByCodeAsc(10L)).thenReturn(List.of(sourceGroup));
        when(groups.save(any(TaxGroup.class))).thenAnswer(invocation -> {
            TaxGroup copy = invocation.getArgument(0);
            copy.setId(150L);
            return copy;
        });
        when(codes.findAllByCompanyIdOrderByCodeAsc(10L)).thenReturn(List.of(sourceCode));

        service().createCompany(created, 10L);

        assertEquals("USD", created.getCurrency());
        assertEquals("QC", created.getDefaultTaxProvince());
        assertEquals(4, created.getFiscalYearStartMonth());
        assertFalse(created.isPostedJournalEditingEnabled());
        ArgumentCaptor<ChartOfAccount> copiedAccount = ArgumentCaptor.forClass(ChartOfAccount.class);
        verify(accounts).save(copiedAccount.capture());
        assertEquals(created, copiedAccount.getValue().getCompany());
        assertNull(copiedAccount.getValue().getOpeningBalance());
        assertNull(copiedAccount.getValue().getOpeningBalanceDate());
        ArgumentCaptor<TaxItem> copiedItem = ArgumentCaptor.forClass(TaxItem.class);
        verify(items).save(copiedItem.capture());
        assertEquals(created, copiedItem.getValue().getCompany());
        assertEquals(130L, copiedItem.getValue().getAgency().getId());
        assertEquals(101L, copiedItem.getValue().getPayableAccount().getId());
        assertNotSame(sourceAgency, copiedItem.getValue().getAgency());
        ArgumentCaptor<TaxGroup> copiedGroup = ArgumentCaptor.forClass(TaxGroup.class);
        verify(groups).save(copiedGroup.capture());
        assertEquals(140L, copiedGroup.getValue().getTaxItems().get(0).getId());
        ArgumentCaptor<TaxCode> copiedCode = ArgumentCaptor.forClass(TaxCode.class);
        verify(codes).save(copiedCode.capture());
        assertEquals(150L, copiedCode.getValue().getSalesTaxGroup().getId());
        assertEquals(150L, copiedCode.getValue().getPurchaseTaxGroup().getId());
        verify(memberships).grant(4L, 20L);
        verify(context).selectCompany(20L);
    }

    @Test
    void refusesCloneSourcesOutsideTheCurrentUsersAccessibleCompanies() {
        Company target = company(null, "New");
        target.setEmail("new@example.test");
        UserAccount user = new UserAccount();
        user.setId(4L);
        when(context.currentUser()).thenReturn(user);
        when(context.canAccessCompany(77L)).thenReturn(false);
        when(companies.findByEmailIgnoreCase("new@example.test")).thenReturn(Optional.empty());

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> service().createCompany(target, 77L));
        verify(companies, never()).save(any());
    }

    @Test
    void administratorCanEnablePostedJournalEditingForAnAccessibleCompany() {
        Company company = company(10L, "Company");
        UserAccount admin = new UserAccount();
        admin.setRole(UserRole.ADMIN);
        when(context.currentUser()).thenReturn(admin);
        when(context.canAccessCompany(10L)).thenReturn(true);
        when(companies.findById(10L)).thenReturn(Optional.of(company));
        when(companies.save(company)).thenReturn(company);

        Company updated = service().setPostedJournalEditingEnabled(10L, true);

        assertTrue(updated.isPostedJournalEditingEnabled());
        verify(companies).save(company);
    }

    @Test
    void refusesPostedJournalEditingChangesForNonAdministrators() {
        Company company = company(10L, "Company");
        UserAccount accountant = new UserAccount();
        accountant.setRole(UserRole.ACCOUNTANT);
        when(context.currentUser()).thenReturn(accountant);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> service().setPostedJournalEditingEnabled(10L, true));

        verify(companies, never()).findById(anyLong());
        verify(companies, never()).save(company);
    }

    private Company company(Long id, String name) {
        Company company = new Company();
        company.setId(id);
        company.setName(name);
        company.setLegalName(name + " Ltd.");
        company.setEmail(name.toLowerCase() + "@example.test");
        company.setPhone("555-0100");
        company.setAddress("1 Main Street");
        company.setCity("Montréal");
        company.setProvince("QC");
        company.setPostalCode("H0H 0H0");
        company.setCountry("Canada");
        return company;
    }

    private ChartOfAccount account(Long id, Company company) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setCompany(company);
        account.setAccountNumber("1000");
        account.setAccountName("Cash");
        account.setAccountType(AccountType.ASSET);
        account.setDescription("Cash account");
        account.setActive(true);
        account.setOpeningBalance(new BigDecimal("125.00"));
        account.setOpeningBalanceDate(LocalDate.of(2025, 1, 1));
        return account;
    }
}
