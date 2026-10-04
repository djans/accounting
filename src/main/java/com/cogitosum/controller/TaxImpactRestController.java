package com.cogitosum.controller;

import com.cogitosum.repository.*;
import com.cogitosum.service.CurrentCompanyContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tax/impact")
public class TaxImpactRestController {

    @Autowired private TaxItemRepository itemRepository;
    @Autowired private TaxFilingPeriodRepository periodRepository;
    @Autowired private TaxCodeRepository codeRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private CurrentCompanyContext companyContext;

    @GetMapping("/agency/{id}")
    public ResponseEntity<Map<String, Object>> getAgencyImpact(@PathVariable Long id) {
        Map<String, Object> impact = new HashMap<>();
        Long companyId = companyContext.requireCompanyId();
        impact.put("items", itemRepository.countByCompanyIdAndAgencyId(companyId, id));
        impact.put("periods", periodRepository.countByCompanyIdAndAgencyId(companyId, id));
        return ResponseEntity.ok(impact);
    }

    @GetMapping("/item/{id}")
    public ResponseEntity<Map<String, Object>> getItemImpact(@PathVariable Long id) {
        Map<String, Object> impact = new HashMap<>();
        // Item is linked to groups. 
        // We might want to count how many groups use this item.
        // But the Repository doesn't have a direct countByItemsId.
        // However, for TaxItem, the impact is mainly through groups it belongs to.
        return ResponseEntity.ok(impact);
    }

    @GetMapping("/group/{id}")
    public ResponseEntity<Map<String, Object>> getGroupImpact(@PathVariable Long id) {
        Map<String, Object> impact = new HashMap<>();
        Long companyId = companyContext.requireCompanyId();
        impact.put("salesCodes", codeRepository.countByCompanyIdAndSalesTaxGroupId(companyId, id));
        impact.put("purchaseCodes", codeRepository.countByCompanyIdAndPurchaseTaxGroupId(companyId, id));
        return ResponseEntity.ok(impact);
    }

    @GetMapping("/code/{id}")
    public ResponseEntity<Map<String, Object>> getCodeImpact(@PathVariable Long id) {
        Map<String, Object> impact = new HashMap<>();
        Long companyId = companyContext.requireCompanyId();
        codeRepository.findByIdAndCompanyId(id, companyId).ifPresent(code -> {
            String codeVal = code.getCode();
            impact.put("invoices", invoiceRepository.countByCompanyIdAndTaxRegime(companyId, codeVal));
            impact.put("bills", billRepository.countByCompanyIdAndVendorProvince(companyId, codeVal));
        });
        return ResponseEntity.ok(impact);
    }
}
