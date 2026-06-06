package com.cogitosum.service;

import com.cogitosum.entity.TaxCode;
import com.cogitosum.repository.TaxCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TaxCodeService {

    @Autowired
    private TaxCodeRepository taxCodeRepository;

    public TaxCode createCode(TaxCode code) {
        return taxCodeRepository.save(code);
    }

    public TaxCode updateCode(Long id, TaxCode code) {
        Optional<TaxCode> existing = taxCodeRepository.findById(id);
        if (existing.isPresent()) {
            TaxCode c = existing.get();
            c.setCode(code.getCode());
            c.setName(code.getName());
            c.setRate(code.getRate());
            c.setAgency(code.getAgency());
            c.setPayableAccount(code.getPayableAccount());
            c.setItcAccount(code.getItcAccount());
            c.setActive(code.getActive());
            return taxCodeRepository.save(c);
        }
        return null;
    }

    public Optional<TaxCode> getById(Long id) {
        return taxCodeRepository.findById(id);
    }

    public Optional<TaxCode> getByCode(String code) {
        return taxCodeRepository.findByCode(code);
    }

    public List<TaxCode> getByAgency(Long agencyId) {
        return taxCodeRepository.findByAgencyId(agencyId);
    }

    public List<TaxCode> getAll() {
        return taxCodeRepository.findAll();
    }

    public List<TaxCode> getActive() {
        return taxCodeRepository.findByIsActive(true);
    }

    public void deleteCode(Long id) {
        taxCodeRepository.deleteById(id);
    }
}
