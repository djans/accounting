package com.cogitosum.service;

import com.cogitosum.entity.Vendor;
import com.cogitosum.repository.VendorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class VendorService {

    @Autowired
    private VendorRepository vendorRepository;

    public Vendor createVendor(Vendor vendor) {
        return vendorRepository.save(vendor);
    }

    public Vendor updateVendor(Long id, Vendor vendor) {
        Optional<Vendor> existingVendor = vendorRepository.findById(id);
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
            v.setSalesTaxAccount(vendor.getSalesTaxAccount());
            v.setPurchaseTaxAccount(vendor.getPurchaseTaxAccount());
            v.setTrackSalesTaxSeparately(vendor.isTrackSalesTaxSeparately());
            v.setTrackPurchaseTaxSeparately(vendor.isTrackPurchaseTaxSeparately());
            v.setTaxOnOtherTaxes(vendor.isTaxOnOtherTaxes());
            v.setTaxIncludedOnExpenses(vendor.isTaxIncludedOnExpenses());
            v.setDefaultExpenseAccount1(vendor.getDefaultExpenseAccount1());
            v.setDefaultExpenseAccount2(vendor.getDefaultExpenseAccount2());
            v.setDefaultExpenseAccount3(vendor.getDefaultExpenseAccount3());
            v.setVendorType(vendor.getVendorType());
            v.setCustomFields(vendor.getCustomFields());
            v.setInactive(vendor.isInactive());
            return vendorRepository.save(v);
        }
        return null;
    }

    public Optional<Vendor> getVendorById(Long id) {
        return vendorRepository.findById(id);
    }

    public Optional<Vendor> getVendorByEmail(String email) {
        return vendorRepository.findByEmail(email);
    }

    public List<Vendor> getAllVendors() {
        return vendorRepository.findAllByOrderByBusinessNameAsc();
    }

    public void deleteVendor(Long id) {
        vendorRepository.deleteById(id);
    }
}
