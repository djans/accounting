package com.cogitosum.entity;

/**
 * A record that is visible only to one company.
 */
public interface CompanyOwned {
    Company getCompany();

    void setCompany(Company company);
}
