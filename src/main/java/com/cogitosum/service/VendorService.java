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
        return vendorRepository.findAll();
    }

    public void deleteVendor(Long id) {
        vendorRepository.deleteById(id);
    }
}
