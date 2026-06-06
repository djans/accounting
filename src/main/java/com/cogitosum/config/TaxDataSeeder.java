package com.cogitosum.config;

import com.cogitosum.entity.*;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.TaxAgencyRepository;
import com.cogitosum.repository.TaxCodeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TaxDataSeeder implements CommandLineRunner {

    private final ChartOfAccountRepository accounts;
    private final TaxAgencyRepository agencies;
    private final TaxCodeRepository codes;

    public TaxDataSeeder(ChartOfAccountRepository accounts,
                         TaxAgencyRepository agencies,
                         TaxCodeRepository codes) {
        this.accounts = accounts;
        this.agencies = agencies;
        this.codes = codes;
    }

    @Override
    public void run(String... args) {
        seedAccounts();
        seedAgencies();
        seedCodes();
    }

    private void seedAccounts() {
        ensureAccount("1000", "Bank / Cash", AccountType.ASSET, "Operating bank account");
        ensureAccount("1100", "Accounts Receivable", AccountType.ASSET, "Amounts owed by customers");
        ensureAccount("2310", "TPS / GST Payable", AccountType.LIABILITY, "Federal sales tax collected");
        ensureAccount("2320", "TVQ / QST Payable", AccountType.LIABILITY, "Quebec sales tax collected");
        ensureAccount("2330", "HST Payable", AccountType.LIABILITY, "Harmonized sales tax collected");
        ensureAccount("4000", "Sales Revenue", AccountType.REVENUE, "Operating revenue");
        ensureAccount("4900", "Tax Adjustments", AccountType.REVENUE, "ITC adjustments on tax filings");
    }

    private void seedAgencies() {
        ensureAgency("CRA", "Canada Revenue Agency", "https://www.canada.ca/en/revenue-agency.html");
        ensureAgency("RQ", "Revenu Quebec", "https://www.revenuquebec.ca/");
    }

    private void seedCodes() {
        TaxAgency cra = agencies.findByCode("CRA").orElseThrow();
        TaxAgency rq = agencies.findByCode("RQ").orElseThrow();
        ChartOfAccount tpsPayable = accounts.findByAccountNumber("2310").orElseThrow();
        ChartOfAccount tvqPayable = accounts.findByAccountNumber("2320").orElseThrow();
        ChartOfAccount hstPayable = accounts.findByAccountNumber("2330").orElseThrow();

        ensureCode("TPS", "Taxe sur les produits et services (5%)", new BigDecimal("0.05000"), cra, tpsPayable);
        ensureCode("TVQ", "Taxe de vente du Quebec (9.975%)", new BigDecimal("0.09975"), rq, tvqPayable);
        ensureCode("HST-ON", "Ontario HST (13%)", new BigDecimal("0.13000"), cra, hstPayable);
        ensureCode("HST-15", "Maritime HST (15%)", new BigDecimal("0.15000"), cra, hstPayable);
    }

    private void ensureAccount(String number, String name, AccountType type, String description) {
        if (accounts.findByAccountNumber(number).isPresent()) return;
        ChartOfAccount a = new ChartOfAccount();
        a.setAccountNumber(number);
        a.setAccountName(name);
        a.setAccountType(type);
        a.setDescription(description);
        a.setActive(true);
        accounts.save(a);
    }

    private void ensureAgency(String code, String name, String website) {
        if (agencies.findByCode(code).isPresent()) return;
        TaxAgency a = new TaxAgency();
        a.setCode(code);
        a.setName(name);
        a.setWebsite(website);
        agencies.save(a);
    }

    private void ensureCode(String code, String name, BigDecimal rate, TaxAgency agency, ChartOfAccount payable) {
        if (codes.findByCode(code).isPresent()) return;
        TaxCode c = new TaxCode();
        c.setCode(code);
        c.setName(name);
        c.setRate(rate);
        c.setAgency(agency);
        c.setPayableAccount(payable);
        c.setActive(true);
        codes.save(c);
    }
}
