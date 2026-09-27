package com.cogitosum.config;

import com.cogitosum.entity.*;
import com.cogitosum.repository.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(2)
@ConditionalOnProperty(name = "app.seed.reference-data.enabled", havingValue = "true", matchIfMissing = true)
public class TaxDataSeeder implements CommandLineRunner {

    private final ChartOfAccountRepository accounts;
    private final TaxAgencyRepository agencies;
    private final TaxCodeRepository codes;
    private final TaxItemRepository items;
    private final TaxGroupRepository taxGroups;
    private final BootstrapCompanyProvider bootstrapCompanyProvider;
    private Company company;

    public TaxDataSeeder(ChartOfAccountRepository accounts,
                         TaxAgencyRepository agencies,
                         TaxCodeRepository codes,
                         TaxItemRepository items,
                         TaxGroupRepository taxGroups,
                         BootstrapCompanyProvider bootstrapCompanyProvider) {
        this.accounts = accounts;
        this.agencies = agencies;
        this.codes = codes;
        this.items = items;
        this.taxGroups = taxGroups;
        this.bootstrapCompanyProvider = bootstrapCompanyProvider;
    }

    @Override
    public void run(String... args) {
        company = bootstrapCompanyProvider.requireBootstrapCompany();
        seedAccounts();
        seedAgencies();
        seedItems();
        seedGroups();
        seedCodes();
    }

    private void seedAccounts() {
        ensureAccount("1000", "Encaisse", AccountType.ASSET, "Argent en banque et en caisse");
        ensureAccount("1010", "Petite caisse", AccountType.ASSET, "Fonds de petite caisse pour menues dépenses");
        ensureAccount("1100", "Comptes clients à recevoir", AccountType.ASSET, "Sommes à recevoir des clients");
        ensureAccount("1110", "Provision pour mauvaises créances", AccountType.ASSET, "Estimation des créances douteuses");
        ensureAccount("1200", "Inventaire", AccountType.ASSET, "Stock de produits ou matières premières");
        ensureAccount("1300", "TPS à recevoir (CTI)", AccountType.ASSET, "Crédit de taxe sur intrants — TPS payée sur les achats, récupérable");
        ensureAccount("1310", "TVQ à recevoir (RTI)", AccountType.ASSET, "Remboursement de taxe sur intrants — TVQ payée sur les achats, récupérable");
        ensureAccount("1320", "HST à recevoir (CTI)", AccountType.ASSET, "Crédit de taxe sur intrants — HST payée sur les achats, récupérable");
        ensureAccount("1330", "TPS/TVH/TVQ_2013", AccountType.ASSET, "Compte de taxe combiné 2013");
        ensureAccount("1400", "Charges payées d'avance", AccountType.ASSET, "Loyer, assurances, abonnements payés d'avance");
        ensureAccount("1500", "Immobilisations", AccountType.ASSET, "Équipement, véhicules, bâtiments");
        ensureAccount("1510", "Amortissement cumulé — Immobilisations", AccountType.ASSET, "Amortissement déduit des immobilisations");

        ensureAccount("2000", "Comptes fournisseurs", AccountType.LIABILITY, "Sommes à payer aux fournisseurs");
        ensureAccount("2100", "Salaires à payer", AccountType.LIABILITY, "Salaires courus mais non versés");

        // Taxes de vente perçues — à remettre aux agences (mouvementés par InvoicePostingService/TaxFilingService).
        ensureAccount("2310", "TPS à payer", AccountType.LIABILITY, "TPS perçue sur les ventes — à remettre à Revenu Canada");
        ensureAccount("2320", "TVQ à payer", AccountType.LIABILITY, "TVQ perçue sur les ventes — à remettre à Revenu Québec");
        ensureAccount("2330", "HST à payer", AccountType.LIABILITY, "HST perçue sur les ventes — à remettre à Revenu Canada");

        // Retenues sur la paie — renumérotées en 24xx pour ne pas chevaucher les taxes de vente.
        ensureAccount("2400", "Impôts fédéraux à payer", AccountType.LIABILITY, "Impôts sur salaires (T4)");
        ensureAccount("2410", "Impôts provinciaux à payer", AccountType.LIABILITY, "Impôts sur salaires (RL-1)");
        ensureAccount("2420", "RRQ à payer", AccountType.LIABILITY, "Cotisations RRQ employé+employeur");
        ensureAccount("2430", "AE à payer", AccountType.LIABILITY, "Cotisations AE employé+employeur");
        ensureAccount("2440", "RQAP à payer", AccountType.LIABILITY, "Cotisations RQAP employé+employeur");
        ensureAccount("2450", "FSS à payer", AccountType.LIABILITY, "Cotisations FSS — employeur");
        ensureAccount("2500", "Emprunt bancaire", AccountType.LIABILITY, "Prêts à plus d'un an");

        ensureAccount("3000", "Capital", AccountType.EQUITY, "Investissement initial du ou des propriétaires");
        ensureAccount("3100", "Bénéfices non répartis", AccountType.EQUITY, "Profits cumulés non distribués");
        ensureAccount("3200", "Retraits du propriétaire", AccountType.EQUITY, "Retraits personnels (entreprise individuelle)");
        ensureAccount("3300", "Sommaire de résultats (clôture)", AccountType.EQUITY, "Compte de regroupement utilisé à la clôture de l'exercice");

        ensureAccount("4000", "Ventes de produits", AccountType.REVENUE, "Revenus des ventes de produits");
        ensureAccount("4010", "Ventes de services", AccountType.REVENUE, "Revenus des services rendus");
        ensureAccount("4100", "Rabais sur ventes", AccountType.REVENUE, "Rabais accordés aux clients (compte contre-revenu)");
        ensureAccount("4200", "Revenus de placements", AccountType.REVENUE, "Intérêts et dividendes reçus");

        ensureAccount("5000", "Coût des marchandises vendues", AccountType.EXPENSE, "Coût direct des produits vendus");

        ensureAccount("6000", "Salaires bruts", AccountType.EXPENSE, "Salaires bruts payés aux employés");
        ensureAccount("6010", "Cotisations employeur RRQ", AccountType.EXPENSE, "Part employeur RRQ");
        ensureAccount("6020", "Cotisations employeur AE", AccountType.EXPENSE, "Part employeur AE");
        ensureAccount("6030", "Cotisations employeur RQAP", AccountType.EXPENSE, "Part employeur RQAP");
        ensureAccount("6040", "Cotisations employeur FSS", AccountType.EXPENSE, "Fonds des services de santé");

        ensureAccount("6100", "Loyer", AccountType.EXPENSE, "Loyer du local commercial");
        ensureAccount("6110", "Électricité", AccountType.EXPENSE, "Facture d'électricité");
        ensureAccount("6120", "Téléphone et Internet", AccountType.EXPENSE, "Communications");

        ensureAccount("6200", "Fournitures de bureau", AccountType.EXPENSE, "Papeterie, cartouches, etc.");
        ensureAccount("6210", "Logiciels et abonnements", AccountType.EXPENSE, "SaaS, licences logicielles");

        ensureAccount("6300", "Frais bancaires", AccountType.EXPENSE, "Frais de tenue de compte, transactions");
        ensureAccount("6310", "Intérêts sur emprunts", AccountType.EXPENSE, "Intérêts payés");

        ensureAccount("6400", "Publicité et marketing", AccountType.EXPENSE, "Annonces, Google Ads, marketing courriel");
        ensureAccount("6410", "Site Web et hébergement", AccountType.EXPENSE, "Hébergement, domaine, développement");

        ensureAccount("6500", "Frais de déplacement", AccountType.EXPENSE, "Essence, taxi, restaurant en déplacement");
        ensureAccount("6510", "Repas et représentation", AccountType.EXPENSE, "50 % déductible — repas d'affaires");

        ensureAccount("6600", "Honoraires professionnels", AccountType.EXPENSE, "Avocats, comptables, consultants");
        ensureAccount("6700", "Assurances", AccountType.EXPENSE, "Assurances commerciales");
        ensureAccount("6800", "Amortissement", AccountType.EXPENSE, "Amortissement des immobilisations");
        ensureAccount("6900", "Mauvaises créances", AccountType.EXPENSE, "Créances irrécupérables");

        ensureAccount("7000", "Impôts sur le revenu", AccountType.EXPENSE, "Impôts corporatifs fédéraux et provinciaux");

    }

    private void seedAgencies() {
        ensureAgency("CRA", "Canada Revenue Agency", "https://www.canada.ca/en/revenue-agency.html");
        ensureAgency("RQ", "Revenu Quebec", "https://www.revenuquebec.ca/");
        ensureAgency("MRQ2013", "Ministere du Revenue (TPS/TVQ_2013)", null);
    }

    private void seedItems() {
        TaxAgency cra = agencies.findByCompanyIdAndCode(company.getId(), "CRA").orElseThrow();
        TaxAgency rq = agencies.findByCompanyIdAndCode(company.getId(), "RQ").orElseThrow();
        TaxAgency mrq2013 = agencies.findByCompanyIdAndCode(company.getId(), "MRQ2013").orElseThrow();

        ChartOfAccount tpsPayable = accounts.findByCompanyIdAndAccountNumber(company.getId(), "2310").orElseThrow();
        ChartOfAccount tvqPayable = accounts.findByCompanyIdAndAccountNumber(company.getId(), "2320").orElseThrow();
        ChartOfAccount hstPayable = accounts.findByCompanyIdAndAccountNumber(company.getId(), "2330").orElseThrow();
        ChartOfAccount tpsItc = accounts.findByCompanyIdAndAccountNumber(company.getId(), "1300").orElseThrow();
        ChartOfAccount tvqItc = accounts.findByCompanyIdAndAccountNumber(company.getId(), "1310").orElseThrow();
        ChartOfAccount hstItc = accounts.findByCompanyIdAndAccountNumber(company.getId(), "1320").orElseThrow();
        ChartOfAccount combined2013 = accounts.findByCompanyIdAndAccountNumber(company.getId(), "1330").orElseThrow();

        ensureItem("TPS", "Taxe sur les produits et services (5%)", new BigDecimal("0.05000"), cra, tpsPayable, tpsItc);
        ensureItem("TVQ", "Taxe de vente du Quebec (9.975%)", new BigDecimal("0.09975"), rq, tvqPayable, tvqItc);
        ensureItem("HST-ON", "Ontario HST (13%)", new BigDecimal("0.13000"), cra, hstPayable, hstItc);
        ensureItem("HST-15", "Maritime HST (15%)", new BigDecimal("0.15000"), cra, hstPayable, hstItc);

        // Données 2013
        ensureItem("S13-TPS", "TPS/GST_2013 (5%)", new BigDecimal("0.05000"), mrq2013, combined2013, combined2013);
        ensureItem("S13-TVQ", "TVQ/QST_2013 (9.98%)", new BigDecimal("0.09980"), mrq2013, combined2013, combined2013);
        ensureItem("S13-TPS-ITC", "TPS(CTI)/GST(ITC)_2013 (5%)", new BigDecimal("0.05000"), mrq2013, combined2013, combined2013);
        ensureItem("S13-TVQ-ITC", "TVQ(CTI)/QST(ITC) (9.98%)", new BigDecimal("0.09980"), mrq2013, combined2013, combined2013);
    }

    private void seedGroups() {
        ensureGroup("QC", "Québec (TPS + TVQ)", "TPS", "TVQ");
        ensureGroup("ON", "Ontario (HST 13%)", "HST-ON");
        ensureGroup("MARITIME", "Maritimes (HST 15%)", "HST-15");
        ensureGroup("FED", "Fédéral (TPS seulement)", "TPS");
    }

    private void seedCodes() {
        ensureCode("QC", "Québec (TPS+TVQ)", "QC", "QC");
        ensureCode("ON", "Ontario (HST 13%)", "ON", "ON");
        ensureCode("MARITIME", "Maritimes (HST 15%)", "MARITIME", "MARITIME");
        ensureCode("FED", "Fédéral (TPS)", "FED", "FED");
    }

    private void ensureGroup(String code, String name, String... itemCodes) {
        if (taxGroups.findByCompanyIdAndCode(company.getId(), code).isPresent()) return;
        TaxGroup g = new TaxGroup();
        g.setCompany(company);
        g.setCode(code);
        g.setName(name);
        for (String ic : itemCodes) {
            items.findByCompanyIdAndCode(company.getId(), ic).ifPresent(g.getTaxItems()::add);
        }
        taxGroups.save(g);
    }

    private void ensureAccount(String number, String name, AccountType type, String description) {
        var existing = accounts.findByCompanyIdAndAccountNumber(company.getId(), number);
        if (existing.isPresent()) {
            ChartOfAccount account = existing.get();
            if (account.getCategory() == null) {
                account.setCategory(defaultCategory(number, type));
                accounts.save(account);
            }
            return;
        }
        ChartOfAccount a = new ChartOfAccount();
        a.setCompany(company);
        a.setAccountNumber(number);
        a.setAccountName(name);
        a.setAccountType(type);
        a.setCategory(defaultCategory(number, type));
        a.setDescription(description);
        a.setActive(true);
        accounts.save(a);
    }

    private AccountCategory defaultCategory(String number, AccountType type) {
        if ("1000".equals(number) || "1010".equals(number)) return AccountCategory.BANK;
        if ("1100".equals(number) || "1110".equals(number)) return AccountCategory.ACCOUNTS_RECEIVABLE;
        if ("2000".equals(number)) return AccountCategory.ACCOUNTS_PAYABLE;
        if ("2500".equals(number)) return AccountCategory.LONG_TERM_LIABILITY;
        if (type == AccountType.ASSET) return AccountCategory.OTHER_CURRENT_ASSET;
        if (type == AccountType.LIABILITY) return AccountCategory.OTHER_CURRENT_LIABILITY;
        if (type == AccountType.EQUITY) return AccountCategory.EQUITY;
        if (type == AccountType.REVENUE) return AccountCategory.INCOME;
        return AccountCategory.EXPENSE;
    }

    private void ensureAgency(String code, String name, String website) {
        if (agencies.findByCompanyIdAndCode(company.getId(), code).isPresent()) return;
        TaxAgency a = new TaxAgency();
        a.setCompany(company);
        a.setCode(code);
        a.setName(name);
        a.setWebsite(website);
        agencies.save(a);
    }

    private void ensureItem(String code, String name, BigDecimal rate, TaxAgency agency,
                            ChartOfAccount payable, ChartOfAccount itc) {
        if (items.findByCompanyIdAndCode(company.getId(), code).isPresent()) return;
        TaxItem i = new TaxItem();
        i.setCompany(company);
        i.setCode(code);
        i.setName(name);
        i.setRate(rate);
        i.setAgency(agency);
        i.setPayableAccount(payable);
        i.setItcAccount(itc);
        i.setForSales(true);
        i.setForPurchases(true);
        i.setActive(true);
        items.save(i);
    }

    private void ensureCode(String code, String name, String salesGroupCode, String purchaseGroupCode) {
        if (codes.findByCompanyIdAndCode(company.getId(), code).isPresent()) return;
        TaxCode c = new TaxCode();
        c.setCompany(company);
        c.setCode(code);
        c.setName(name);
        taxGroups.findByCompanyIdAndCode(company.getId(), salesGroupCode).ifPresent(c::setSalesTaxGroup);
        taxGroups.findByCompanyIdAndCode(company.getId(), purchaseGroupCode).ifPresent(c::setPurchaseTaxGroup);
        c.setActive(true);
        codes.save(c);
    }
}
