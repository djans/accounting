package com.cogitosum.service;

import com.cogitosum.entity.TaxCode;
import com.cogitosum.entity.TaxGroup;
import com.cogitosum.repository.TaxCodeRepository;
import com.cogitosum.repository.TaxGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TaxCodeService {

    @Autowired
    private TaxCodeRepository taxCodeRepository;

    @Autowired
    private TaxGroupRepository taxGroupRepository;

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
        return taxCodeRepository.findByAgencyIdOrderByCodeAsc(agencyId);
    }

    public List<TaxCode> getAll() {
        return taxCodeRepository.findAllByOrderByCodeAsc();
    }

    public List<TaxCode> getActive() {
        return taxCodeRepository.findByIsActiveOrderByCodeAsc(true);
    }

    public void deleteCode(Long id) {
        taxCodeRepository.deleteById(id);
    }

    public void deleteGroup(Long id) {
        taxGroupRepository.deleteById(id);
    }

    // Tax Groups
    public TaxGroup createGroup(TaxGroup group) {
        return taxGroupRepository.save(group);
    }

    public TaxGroup updateGroup(Long id, TaxGroup group) {
        TaxGroup existing = taxGroupRepository.findById(id).orElseThrow();
        existing.setCode(group.getCode());
        existing.setName(group.getName());
        existing.setTaxItems(group.getTaxItems());
        existing.setActive(group.getActive());
        return taxGroupRepository.save(existing);
    }

    public List<TaxGroup> getAllGroups() {
        return taxGroupRepository.findAllByOrderByCodeAsc();
    }

    public List<TaxGroup> getActiveGroups() {
        return taxGroupRepository.findByIsActiveOrderByCodeAsc(true);
    }

    public Optional<TaxGroup> getGroupById(Long id) {
        return taxGroupRepository.findById(id);
    }
}
