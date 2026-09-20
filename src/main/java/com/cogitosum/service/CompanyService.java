package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.repository.CompanyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    public Company createCompany(Company company) {
        return companyRepository.save(company);
    }

    public List<Company> getAllCompanies() {
        return companyRepository.findAll();
    }

    public Optional<Company> getCompanyById(Long id) {
        return companyRepository.findById(id);
    }

    public Optional<Company> getCompanyByEmail(String email) {
        return companyRepository.findByEmail(email);
    }

    public Company updateCompany(Long id, Company company) {
        Optional<Company> existing = companyRepository.findById(id);
        if (existing.isEmpty()) {
            return null;
        }
        Company current = existing.get();
        current.setName(company.getName());
        current.setLegalName(company.getLegalName());
        current.setEmail(company.getEmail());
        current.setPhone(company.getPhone());
        current.setAddress(company.getAddress());
        current.setCity(company.getCity());
        current.setProvince(company.getProvince());
        current.setPostalCode(company.getPostalCode());
        current.setCountry(company.getCountry());
        current.setBusinessNumber(company.getBusinessNumber());
        current.setGstNumber(company.getGstNumber());
        current.setQstNumber(company.getQstNumber());
        current.setCurrency(company.getCurrency());
        current.setDefaultTaxProvince(company.getDefaultTaxProvince());
        current.setFiscalYearStartMonth(company.getFiscalYearStartMonth());
        return companyRepository.save(current);
    }

    public void deleteCompany(Long id) {
        companyRepository.deleteById(id);
    }
}
