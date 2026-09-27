package com.cogitosum.service;

import com.cogitosum.entity.TaxCode;
import com.cogitosum.entity.TaxGroup;
import com.cogitosum.entity.TaxItem;
import com.cogitosum.repository.TaxCodeRepository;
import com.cogitosum.repository.TaxGroupRepository;
import com.cogitosum.repository.TaxItemRepository;
import com.cogitosum.repository.TaxAgencyRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TaxCodeService {

    @Autowired
    private TaxCodeRepository taxCodeRepository;

    @Autowired
    private TaxItemRepository taxItemRepository;

    @Autowired
    private TaxGroupRepository taxGroupRepository;

    @Autowired
    private TaxAgencyRepository taxAgencyRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private CurrentCompanyContext companyContext;

    // Tax Items (previously TaxCodes)
    public TaxItem createItem(TaxItem item) {
        Long companyId = companyContext.requireCompanyId();
        item.setCompany(companyContext.requireCompany());
        resolveItemReferences(item, companyId);
        return taxItemRepository.save(item);
    }

    public TaxItem updateItem(Long id, TaxItem item) {
        Long companyId = companyContext.requireCompanyId();
        Optional<TaxItem> existing = taxItemRepository.findByIdAndCompanyId(id, companyId);
        if (existing.isPresent()) {
            TaxItem c = existing.get();
            c.setCode(item.getCode());
            c.setName(item.getName());
            c.setRate(item.getRate());
            resolveItemReferences(item, companyId);
            c.setAgency(item.getAgency());
            c.setPayableAccount(item.getPayableAccount());
            c.setItcAccount(item.getItcAccount());
            c.setActive(item.getActive());
            c.setForSales(item.getForSales());
            c.setForPurchases(item.getForPurchases());
            return taxItemRepository.save(c);
        }
        return null;
    }

    public Optional<TaxItem> getItemById(Long id) {
        return taxItemRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public List<TaxItem> getAllItems() {
        return taxItemRepository.findAllByCompanyIdOrderByCodeAsc(companyContext.requireCompanyId());
    }

    public List<TaxItem> getActiveItems() {
        return taxItemRepository.findByCompanyIdAndIsActiveOrderByCodeAsc(companyContext.requireCompanyId(), true);
    }

    public List<TaxItem> getItemsByAgency(Long agencyId) {
        return taxItemRepository.findByCompanyIdAndAgencyIdOrderByCodeAsc(companyContext.requireCompanyId(), agencyId);
    }

    @Transactional
    public void deleteItem(Long id) {
        getItemById(id).ifPresent(item -> {
            item.setActive(false);
            taxItemRepository.save(item);
        });
    }

    // Tax Groups
    public TaxGroup createGroup(TaxGroup group) {
        Long companyId = companyContext.requireCompanyId();
        group.setCompany(companyContext.requireCompany());
        group.setTaxItems(resolveItems(group.getTaxItems(), companyId));
        return taxGroupRepository.save(group);
    }

    public TaxGroup updateGroup(Long id, TaxGroup group) {
        Long companyId = companyContext.requireCompanyId();
        TaxGroup existing = taxGroupRepository.findByIdAndCompanyId(id, companyId).orElseThrow();
        existing.setCode(group.getCode());
        existing.setName(group.getName());
        existing.setTaxItems(resolveItems(group.getTaxItems(), companyId));
        existing.setActive(group.getActive());
        return taxGroupRepository.save(existing);
    }

    public List<TaxGroup> getAllGroups() {
        return taxGroupRepository.findAllByCompanyIdOrderByCodeAsc(companyContext.requireCompanyId());
    }

    public List<TaxGroup> getActiveGroups() {
        return taxGroupRepository.findByCompanyIdAndIsActiveOrderByCodeAsc(companyContext.requireCompanyId(), true);
    }

    public Optional<TaxGroup> getGroupById(Long id) {
        return taxGroupRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    @Transactional
    public void deleteGroup(Long id) {
        getGroupById(id).ifPresent(group -> {
            group.setActive(false);
            taxGroupRepository.save(group);
        });
    }

    // New Tax Codes (pointing to groups)
    public TaxCode createCode(TaxCode code) {
        Long companyId = companyContext.requireCompanyId();
        code.setCompany(companyContext.requireCompany());
        resolveCodeReferences(code, companyId);
        return taxCodeRepository.save(code);
    }

    public TaxCode updateCode(Long id, TaxCode code) {
        Long companyId = companyContext.requireCompanyId();
        TaxCode existing = taxCodeRepository.findByIdAndCompanyId(id, companyId).orElseThrow();
        existing.setCode(code.getCode());
        existing.setName(code.getName());
        resolveCodeReferences(code, companyId);
        existing.setSalesTaxGroup(code.getSalesTaxGroup());
        existing.setPurchaseTaxGroup(code.getPurchaseTaxGroup());
        existing.setActive(code.getActive());
        return taxCodeRepository.save(existing);
    }

    public Optional<TaxCode> getCodeById(Long id) {
        return taxCodeRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public List<TaxCode> getAllCodes() {
        return taxCodeRepository.findAllByCompanyIdOrderByCodeAsc(companyContext.requireCompanyId());
    }

    public List<TaxCode> getActiveCodes() {
        return taxCodeRepository.findByCompanyIdAndIsActiveOrderByCodeAsc(companyContext.requireCompanyId(), true);
    }

    public void deleteCode(Long id) {
        getCodeById(id).ifPresent(code -> {
            code.setActive(false);
            taxCodeRepository.save(code);
        });
    }

    private void resolveItemReferences(TaxItem item, Long companyId) {
        if (item.getAgency() == null || item.getAgency().getId() == null) {
            throw new IllegalArgumentException("Tax agency is required");
        }
        item.setAgency(taxAgencyRepository.findByIdAndCompanyId(item.getAgency().getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Tax agency not found")));
        item.setPayableAccount(resolveAccount(item.getPayableAccount(), companyId));
        item.setItcAccount(resolveAccount(item.getItcAccount(), companyId));
    }

    private com.cogitosum.entity.ChartOfAccount resolveAccount(
            com.cogitosum.entity.ChartOfAccount account, Long companyId) {
        if (account == null) {
            return null;
        }
        if (account.getId() == null) {
            throw new IllegalArgumentException("Tax account is invalid");
        }
        return accountRepository.findByIdAndCompanyId(account.getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Tax account not found"));
    }

    private List<TaxItem> resolveItems(List<TaxItem> items, Long companyId) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(item -> {
            if (item == null || item.getId() == null) {
                throw new IllegalArgumentException("Tax item is invalid");
            }
            return taxItemRepository.findByIdAndCompanyId(item.getId(), companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Tax item not found"));
        }).toList();
    }

    private void resolveCodeReferences(TaxCode code, Long companyId) {
        code.setSalesTaxGroup(resolveGroup(code.getSalesTaxGroup(), companyId));
        code.setPurchaseTaxGroup(resolveGroup(code.getPurchaseTaxGroup(), companyId));
    }

    private TaxGroup resolveGroup(TaxGroup group, Long companyId) {
        if (group == null) {
            return null;
        }
        if (group.getId() == null) {
            throw new IllegalArgumentException("Tax group is invalid");
        }
        return taxGroupRepository.findByIdAndCompanyId(group.getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Tax group not found"));
    }
}
