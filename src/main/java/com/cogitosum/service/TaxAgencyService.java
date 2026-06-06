package com.cogitosum.service;

import com.cogitosum.entity.TaxAgency;
import com.cogitosum.repository.TaxAgencyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TaxAgencyService {

    @Autowired
    private TaxAgencyRepository taxAgencyRepository;

    public TaxAgency createAgency(TaxAgency agency) {
        return taxAgencyRepository.save(agency);
    }

    public TaxAgency updateAgency(Long id, TaxAgency agency) {
        Optional<TaxAgency> existing = taxAgencyRepository.findById(id);
        if (existing.isPresent()) {
            TaxAgency a = existing.get();
            a.setCode(agency.getCode());
            a.setName(agency.getName());
            a.setAddress(agency.getAddress());
            a.setWebsite(agency.getWebsite());
            a.setAccountNumber(agency.getAccountNumber());
            return taxAgencyRepository.save(a);
        }
        return null;
    }

    public Optional<TaxAgency> getById(Long id) {
        return taxAgencyRepository.findById(id);
    }

    public Optional<TaxAgency> getByCode(String code) {
        return taxAgencyRepository.findByCode(code);
    }

    public List<TaxAgency> getAll() {
        return taxAgencyRepository.findAll();
    }

    public void deleteAgency(Long id) {
        taxAgencyRepository.deleteById(id);
    }
}
