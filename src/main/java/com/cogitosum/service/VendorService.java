package com.cogitosum.service;

import com.cogitosum.entity.Vendor;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.VendorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class VendorService {

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private CurrentCompanyContext companyContext;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    public Vendor createVendor(Vendor vendor) {
        Long companyId = companyContext.requireCompanyId();
        companyContext.assignCurrentCompany(vendor);
        applyAccounts(vendor, vendor, companyId);
        return vendorRepository.save(vendor);
    }

    public Vendor updateVendor(Long id, Vendor vendor) {
        Optional<Vendor> existingVendor = vendorRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
        if (existingVendor.isPresent()) {
            Vendor v = existingVendor.get();
            v.setName(vendor.getName());
            v.setEmail(vendor.getEmail());
            v.setBusinessName(vendor.getBusinessName());
            v.setAddress(vendor.getAddress());
            v.setCity(vendor.getCity());
            v.setProvince(vendor.getProvince());
            v.setPostalCode(vendor.getPostalCode());
            v.setCountry(vendor.getCountry());
            v.setBusinessNumber(vendor.getBusinessNumber());
            v.setGstNumber(vendor.getGstNumber());
            v.setQstNumber(vendor.getQstNumber());
            v.setCompanyName(vendor.getCompanyName());
            v.setTitle(vendor.getTitle());
            v.setFirstName(vendor.getFirstName());
            v.setMiddleInitial(vendor.getMiddleInitial());
            v.setLastName(vendor.getLastName());
            v.setJobTitle(vendor.getJobTitle());
            v.setMainPhone(vendor.getMainPhone());
            v.setWorkPhone(vendor.getWorkPhone());
            v.setMobilePhone(vendor.getMobilePhone());
            v.setFax(vendor.getFax());
            v.setWebsite(vendor.getWebsite());
            v.setSecondaryEmail(vendor.getSecondaryEmail());
            v.setCcEmail(vendor.getCcEmail());
            v.setShipFromAddress(vendor.getShipFromAddress());
            v.setAccountNumber(vendor.getAccountNumber());
            v.setPaymentTerms(vendor.getPaymentTerms());
            v.setBillingRateLevel(vendor.getBillingRateLevel());
            v.setPrintOnChequeAs(vendor.getPrintOnChequeAs());
            v.setCreditLimit(vendor.getCreditLimit());
            v.setTaxAgency(vendor.isTaxAgency());
            v.setTaxReturnType(vendor.getTaxReturnType());
            v.setTaxReportingPeriod(vendor.getTaxReportingPeriod());
            v.setTaxPeriodEnding(vendor.getTaxPeriodEnding());
            v.setTaxLabel(vendor.getTaxLabel());
            v.setSalesTaxRegistrationNumber(vendor.getSalesTaxRegistrationNumber());
            Long companyId = companyContext.requireCompanyId();
            v.setSalesTaxAccount(resolveAccount(vendor.getSalesTaxAccount(), companyId));
            v.setPurchaseTaxAccount(resolveAccount(vendor.getPurchaseTaxAccount(), companyId));
            v.setTrackSalesTaxSeparately(vendor.isTrackSalesTaxSeparately());
            v.setTrackPurchaseTaxSeparately(vendor.isTrackPurchaseTaxSeparately());
            v.setTaxOnOtherTaxes(vendor.isTaxOnOtherTaxes());
            v.setTaxIncludedOnExpenses(vendor.isTaxIncludedOnExpenses());
            v.setDefaultExpenseAccount1(resolveAccount(vendor.getDefaultExpenseAccount1(), companyId));
            v.setDefaultExpenseAccount2(resolveAccount(vendor.getDefaultExpenseAccount2(), companyId));
            v.setDefaultExpenseAccount3(resolveAccount(vendor.getDefaultExpenseAccount3(), companyId));
            v.setVendorType(vendor.getVendorType());
            v.setCustomFields(vendor.getCustomFields());
            v.setInactive(vendor.isInactive());
            return vendorRepository.save(v);
        }
        return null;
    }

    private void applyAccounts(Vendor target, Vendor source, Long companyId) {
        target.setSalesTaxAccount(resolveAccount(source.getSalesTaxAccount(), companyId));
        target.setPurchaseTaxAccount(resolveAccount(source.getPurchaseTaxAccount(), companyId));
        target.setDefaultExpenseAccount1(resolveAccount(source.getDefaultExpenseAccount1(), companyId));
        target.setDefaultExpenseAccount2(resolveAccount(source.getDefaultExpenseAccount2(), companyId));
        target.setDefaultExpenseAccount3(resolveAccount(source.getDefaultExpenseAccount3(), companyId));
    }

    private ChartOfAccount resolveAccount(ChartOfAccount account, Long companyId) {
        if (account == null) {
            return null;
        }
        if (account.getId() == null) {
            throw new IllegalArgumentException("Account is invalid");
        }
        return chartOfAccountRepository.findByIdAndCompanyId(account.getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    public Optional<Vendor> getVendorById(Long id) {
        return vendorRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public Optional<Vendor> getVendorByEmail(String email) {
        return vendorRepository.findByCompanyIdAndEmail(companyContext.requireCompanyId(), email);
    }

    public List<Vendor> getAllVendors() {
        return vendorRepository.findAllByCompanyIdOrderByBusinessNameAsc(companyContext.requireCompanyId());
    }

    public void deleteVendor(Long id) {
        vendorRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId())
                .ifPresent(vendorRepository::delete);
    }
}
