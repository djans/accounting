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

    @Autowired
    private CurrentCompanyContext companyContext;

    public TaxAgency createAgency(TaxAgency agency) {
        companyContext.assignCurrentCompany(agency);
        return taxAgencyRepository.save(agency);
    }

    public TaxAgency updateAgency(Long id, TaxAgency agency) {
        Optional<TaxAgency> existing = taxAgencyRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
        if (existing.isPresent()) {
            TaxAgency a = existing.get();
            a.setCode(agency.getCode());
            a.setName(agency.getName());
            a.setAddress(agency.getAddress());
            a.setWebsite(agency.getWebsite());
            a.setAccountNumber(agency.getAccountNumber());
            a.setActive(agency.getActive());
            return taxAgencyRepository.save(a);
        }
        return null;
    }

    public Optional<TaxAgency> getById(Long id) {
        return taxAgencyRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public Optional<TaxAgency> getByCode(String code) {
        return taxAgencyRepository.findByCompanyIdAndCode(companyContext.requireCompanyId(), code);
    }

    public List<TaxAgency> getAll() {
        return taxAgencyRepository.findAllByCompanyIdOrderByCodeAsc(companyContext.requireCompanyId());
    }

    public List<TaxAgency> getActiveAgencies() {
        return taxAgencyRepository.findByCompanyIdAndIsActiveOrderByCodeAsc(companyContext.requireCompanyId(), true);
    }

    public void deleteAgency(Long id) {
        Optional<TaxAgency> existing = taxAgencyRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
        if (existing.isPresent()) {
            TaxAgency a = existing.get();
            a.setActive(false);
            taxAgencyRepository.save(a);
        }
    }
}
