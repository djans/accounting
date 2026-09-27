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

    @Autowired
    private CurrentCompanyContext companyContext;

    public Company createCompany(Company company) {
        throw new UnsupportedOperationException("Companies are created only during controlled bootstrap");
    }

    public List<Company> getAllCompanies() {
        return List.of(companyContext.requireCompany());
    }

    public Optional<Company> getCompanyById(Long id) {
        Company current = companyContext.requireCompany();
        return current.getId().equals(id) ? Optional.of(current) : Optional.empty();
    }

    public Optional<Company> getCompanyByEmail(String email) {
        return getAllCompanies().stream().filter(company -> company.getEmail().equalsIgnoreCase(email)).findFirst();
    }

    public Company updateCompany(Long id, Company company) {
        Company current = companyContext.requireCompany();
        if (!current.getId().equals(id)) {
            return null;
        }
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
        throw new UnsupportedOperationException("Companies cannot be deleted through the application");
    }
}
