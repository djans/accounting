package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

@Service
public class CompanyService {

    private static final int MAX_LOGO_BYTES = 1_048_576;
    private static final int MAX_LOGO_DIMENSION = 4096;
    private static final long MAX_LOGO_PIXELS = 16_777_216L;

    private final CompanyRepository companyRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final CurrentCompanyContext companyContext;
    private final ChartOfAccountRepository accountRepository;
    private final TaxAgencyRepository agencyRepository;
    private final TaxItemRepository taxItemRepository;
    private final TaxGroupRepository taxGroupRepository;
    private final TaxCodeRepository taxCodeRepository;

    public CompanyService(CompanyRepository companyRepository,
                          CompanyMembershipRepository membershipRepository,
                          CurrentCompanyContext companyContext,
                          ChartOfAccountRepository accountRepository,
                          TaxAgencyRepository agencyRepository,
                          TaxItemRepository taxItemRepository,
                          TaxGroupRepository taxGroupRepository,
                          TaxCodeRepository taxCodeRepository) {
        this.companyRepository = companyRepository;
        this.membershipRepository = membershipRepository;
        this.companyContext = companyContext;
        this.accountRepository = accountRepository;
        this.agencyRepository = agencyRepository;
        this.taxItemRepository = taxItemRepository;
        this.taxGroupRepository = taxGroupRepository;
        this.taxCodeRepository = taxCodeRepository;
    }

    @Transactional
    public Company createCompany(Company company) {
        return createCompany(company, null);
    }

    @Transactional
    public Company createCompany(Company company, Long sourceCompanyId) {
        return createCompany(company, sourceCompanyId, null, null);
    }

    @Transactional
    public Company createCompany(Company company, Long sourceCompanyId, byte[] logoData, String logoContentType) {
        if (!companyContext.isSuperAdmin()) {
            throw new AccessDeniedException("Only SuperAdmins can create companies");
        }
        LogoUpload logo = validateLogo(logoData, logoContentType);
        validateCompany(company);
        UserAccount user = companyContext.currentUser();
        company.setPostedJournalEditingEnabled(false);
        if (companyRepository.findByEmailIgnoreCase(company.getEmail()).isPresent()) {
            throw new IllegalArgumentException("A company with this email already exists");
        }

        Company source = null;
        if (sourceCompanyId != null) {
            if (!companyContext.canAccessCompany(sourceCompanyId)) {
                throw new AccessDeniedException("The selected source company is not available to this user");
            }
            source = companyRepository.findById(sourceCompanyId)
                .orElseThrow(() -> new IllegalArgumentException("The selected source company does not exist"));
        }

        if (source != null) {
            company.setCurrency(source.getCurrency());
            company.setDefaultTaxProvince(source.getDefaultTaxProvince());
            company.setFiscalYearStartMonth(source.getFiscalYearStartMonth());
        }
        company.setId(null);
        company.setCreatedAt(null);
        company.setUpdatedAt(null);
        company.setLogoData(logo == null ? null : logo.data());
        company.setLogoContentType(logo == null ? null : logo.contentType());
        Company created = companyRepository.save(company);
        if (source != null) {
            cloneChartOfAccounts(source, created);
            cloneTaxConfiguration(source, created);
        }
        membershipRepository.grant(user.getId(), created.getId(), UserRole.ADMIN);
        companyContext.selectCompany(created.getId());
        return created;
    }

    @Transactional(readOnly = true)
    public List<Company> getAllCompanies() {
        return companyContext.getAccessibleCompanies();
    }

    @Transactional(readOnly = true)
    public Optional<Company> getCompanyById(Long id) {
        return companyContext.getAccessibleCompanies().stream()
            .filter(company -> company.getId().equals(id)).findFirst();
    }

    @Transactional(readOnly = true)
    public Company getCompanyForEdit(Long id) {
        return editableCompany(id);
    }

    @Transactional(readOnly = true)
    public Optional<Company> getCompanyByEmail(String email) {
        return getAllCompanies().stream().filter(company -> company.getEmail().equalsIgnoreCase(email)).findFirst();
    }

    @Transactional
    public Company updateCompany(Long id, Company company) {
        return updateCompany(id, company, null, null, false);
    }

    @Transactional
    public Company updateCompany(Long id, Company company, byte[] logoData, String logoContentType,
                                 boolean removeLogo) {
        if (removeLogo && logoData != null) {
            throw new IllegalArgumentException("Choose either a new logo or remove the existing logo");
        }
        LogoUpload logo = validateLogo(logoData, logoContentType);
        Company current = editableCompany(id);
        validateCompany(company);
        boolean emailInUse = companyRepository.findByEmailIgnoreCase(company.getEmail())
                .filter(existing -> !existing.getId().equals(current.getId()))
                .isPresent();
        if (emailInUse) {
            throw new IllegalArgumentException("A company with this email already exists");
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
        if (removeLogo) {
            current.setLogoData(null);
            current.setLogoContentType(null);
        } else if (logo != null) {
            current.setLogoData(logo.data());
            current.setLogoContentType(logo.contentType());
        }
        return companyRepository.save(current);
    }

    @Transactional
    public Company setPostedJournalEditingEnabled(Long id, boolean enabled) {
        if (!companyContext.canAdministerCompany(id)) {
            throw new AccessDeniedException("Only administrators of the requested company can change its settings");
        }
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        company.setPostedJournalEditingEnabled(enabled);
        return companyRepository.save(company);
    }

    public void deleteCompany(Long id) {
        throw new UnsupportedOperationException("Companies cannot be deleted through the application");
    }

    private void validateCompany(Company company) {
        if (company == null || blank(company.getName()) || blank(company.getLegalName())
                || blank(company.getEmail()) || !company.getEmail().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || blank(company.getPhone()) || blank(company.getAddress())
                || blank(company.getCity()) || blank(company.getProvince()) || blank(company.getPostalCode())
                || blank(company.getCountry())) {
            throw new IllegalArgumentException("Company identity, email, phone, and address fields are required");
        }
        company.setEmail(company.getEmail().trim());
    }

    private Company editableCompany(Long id) {
        if (!companyContext.canAdministerCompany(id)) {
            throw new AccessDeniedException("Only administrators of the requested company can edit its details");
        }
        Company selected = companyContext.requireCompany();
        if (!selected.getId().equals(id)) {
            throw new AccessDeniedException("Only the selected company can be edited");
        }
        return companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private LogoUpload validateLogo(byte[] data, String contentType) {
        if (data == null) {
            if (contentType != null && !contentType.isBlank()) {
                throw new IllegalArgumentException("A logo image is required");
            }
            return null;
        }
        if (data.length == 0 || data.length > MAX_LOGO_BYTES) {
            throw new IllegalArgumentException("Company logos must be non-empty and no larger than 1 MiB");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Company logo must be a PNG or JPEG image");
        }

        String requestedType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (imageInput == null) {
                throw new IllegalArgumentException("Company logo must be a valid PNG or JPEG image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Company logo must be a valid PNG or JPEG image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String actualType = switch (format) {
                    case "png" -> "image/png";
                    case "jpeg", "jpg" -> "image/jpeg";
                    default -> throw new IllegalArgumentException("Company logo must be PNG or JPEG");
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (!actualType.equals(requestedType) || width < 1 || height < 1
                        || width > MAX_LOGO_DIMENSION || height > MAX_LOGO_DIMENSION
                        || (long) width * height > MAX_LOGO_PIXELS || reader.read(0) == null) {
                    throw new IllegalArgumentException("Company logo is invalid or exceeds the supported dimensions");
                }
                return new LogoUpload(data.clone(), actualType);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read the company logo image", exception);
        }
    }

    private void cloneChartOfAccounts(Company source, Company target) {
        List<ChartOfAccount> sourceAccounts = accountRepository.findAllByCompanyIdOrderByAccountNumberAsc(source.getId());
        Map<Long, ChartOfAccount> accountMap = new HashMap<>();
        Map<Long, Long> parentIds = new HashMap<>();
        for (ChartOfAccount original : sourceAccounts) {
            ChartOfAccount copy = new ChartOfAccount();
            copy.setCompany(target);
            copy.setAccountNumber(original.getAccountNumber());
            copy.setAccountName(original.getAccountName());
            copy.setAccountType(original.getAccountType());
            copy.setCategory(original.getCategory());
            copy.setDescription(original.getDescription());
            copy.setActive(original.getActive());
            copy.setCurrency(original.getCurrency());
            copy.setOpeningBalance(null);
            copy.setOpeningBalanceDate(null);
            ChartOfAccount saved = accountRepository.save(copy);
            accountMap.put(original.getId(), saved);
            if (original.getParentAccount() != null) {
                parentIds.put(saved.getId(), original.getParentAccount().getId());
            }
        }
        for (Map.Entry<Long, Long> entry : parentIds.entrySet()) {
            ChartOfAccount child = accountMap.values().stream()
                .filter(account -> account.getId().equals(entry.getKey())).findFirst().orElseThrow();
            child.setParentAccount(accountMap.get(entry.getValue()));
            accountRepository.save(child);
        }
    }

    private void cloneTaxConfiguration(Company source, Company target) {
        Map<Long, ChartOfAccount> targetAccounts = new HashMap<>();
        for (ChartOfAccount account : accountRepository.findAllByCompanyIdOrderByAccountNumberAsc(target.getId())) {
            targetAccounts.put(account.getId(), account);
        }
        Map<String, ChartOfAccount> accountsByNumber = new HashMap<>();
        for (ChartOfAccount account : targetAccounts.values()) {
            accountsByNumber.put(account.getAccountNumber(), account);
        }
        Map<Long, TaxAgency> agencies = new HashMap<>();
        for (TaxAgency original : agencyRepository.findAllByCompanyIdOrderByCodeAsc(source.getId())) {
            TaxAgency copy = new TaxAgency();
            copy.setCompany(target);
            copy.setCode(original.getCode());
            copy.setName(original.getName());
            copy.setAddress(original.getAddress());
            copy.setWebsite(original.getWebsite());
            copy.setAccountNumber(original.getAccountNumber());
            copy.setActive(original.getActive());
            agencies.put(original.getId(), agencyRepository.save(copy));
        }

        Map<Long, TaxItem> items = new HashMap<>();
        for (TaxItem original : taxItemRepository.findAllByCompanyIdOrderByCodeAsc(source.getId())) {
            TaxItem copy = new TaxItem();
            copy.setCompany(target);
            copy.setCode(original.getCode());
            copy.setName(original.getName());
            copy.setRate(original.getRate());
            copy.setForSales(original.getForSales());
            copy.setForPurchases(original.getForPurchases());
            copy.setSalesReturnLine(original.getSalesReturnLine());
            copy.setPurchaseReturnLine(original.getPurchaseReturnLine());
            copy.setAgency(agencies.get(original.getAgency().getId()));
            copy.setPayableAccount(remapAccount(original.getPayableAccount(), accountsByNumber));
            copy.setItcAccount(remapAccount(original.getItcAccount(), accountsByNumber));
            copy.setActive(original.getActive());
            items.put(original.getId(), taxItemRepository.save(copy));
        }

        Map<Long, TaxGroup> groups = new HashMap<>();
        for (TaxGroup original : taxGroupRepository.findAllByCompanyIdOrderByCodeAsc(source.getId())) {
            TaxGroup copy = new TaxGroup();
            copy.setCompany(target);
            copy.setCode(original.getCode());
            copy.setName(original.getName());
            copy.setActive(original.getActive());
            copy.setTaxItems(new ArrayList<>(original.getTaxItems().stream()
                .map(item -> items.get(item.getId())).filter(Objects::nonNull).toList()));
            groups.put(original.getId(), taxGroupRepository.save(copy));
        }

        for (TaxCode original : taxCodeRepository.findAllByCompanyIdOrderByCodeAsc(source.getId())) {
            TaxCode copy = new TaxCode();
            copy.setCompany(target);
            copy.setCode(original.getCode());
            copy.setName(original.getName());
            copy.setActive(original.getActive());
            copy.setSalesTaxGroup(original.getSalesTaxGroup() == null ? null : groups.get(original.getSalesTaxGroup().getId()));
            copy.setPurchaseTaxGroup(original.getPurchaseTaxGroup() == null
                ? null : groups.get(original.getPurchaseTaxGroup().getId()));
            taxCodeRepository.save(copy);
        }
    }

    private ChartOfAccount remapAccount(ChartOfAccount sourceAccount, Map<String, ChartOfAccount> targetAccounts) {
        return sourceAccount == null ? null : targetAccounts.get(sourceAccount.getAccountNumber());
    }

    private record LogoUpload(byte[] data, String contentType) {
    }
}
