package com.cogitosum.controller;

import com.cogitosum.entity.TaxAgency;
import com.cogitosum.entity.TaxCode;
import com.cogitosum.entity.TaxItem;
import com.cogitosum.service.TaxAgencyService;
import com.cogitosum.service.TaxCodeService;
import com.cogitosum.service.TaxFilingService;
import com.cogitosum.service.CurrentCompanyContext;
import com.cogitosum.dto.TaxReturnRowDTO;
import com.cogitosum.repository.GeneralLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/tax")
public class TaxReportRestController {

    @Autowired private TaxAgencyService agencyService;
    @Autowired private TaxCodeService codeService;
    @Autowired private TaxFilingService filingService;
    @Autowired private GeneralLedgerRepository ledgerRepository;
    @Autowired private CurrentCompanyContext companyContext;
    @Autowired private MessageSource messageSource;

    @GetMapping("/reconciliation")
    public ResponseEntity<List<Map<String, Object>>> reconciliation() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TaxAgency agency : agencyService.getAll()) {
            for (TaxItem item : codeService.getItemsByAgency(agency.getId())) {
                if (item.getPayableAccount() == null) continue;
                Map<String, Object> row = new HashMap<>();
                row.put("agency", agency);
                row.put("code", item);
                row.put("account", item.getPayableAccount());
                BigDecimal balance = ledgerRepository.findByCompanyIdAndAccountId(
                        companyContext.requireCompanyId(), item.getPayableAccount().getId())
                    .map(gl -> gl.getCreditBalance().subtract(gl.getDebitBalance()))
                    .orElse(BigDecimal.ZERO);
                row.put("glBalance", balance);
                rows.add(row);
            }
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/agencies")
    public ResponseEntity<List<TaxAgency>> agencies() {
        return ResponseEntity.ok(agencyService.getAll());
    }

    @GetMapping("/codes")
    public ResponseEntity<List<TaxCode>> codes() {
        return ResponseEntity.ok(codeService.getAllCodes());
    }

    @GetMapping("/periods")
    public ResponseEntity<List<?>> periods() {
        return ResponseEntity.ok(filingService.getAll());
    }

    @GetMapping("/periods/{id}")
    public ResponseEntity<?> period(@org.springframework.web.bind.annotation.PathVariable("id") Long id) {
        return filingService.getById(id)
            .map(p -> {
                Map<String, Object> body = new HashMap<>();
                body.put("period", p);
                body.put("collectedDetail", filingService.getTaxCollectedDetail(p.getAgency(), p.getPeriodStart(), p.getPeriodEnd()));
                body.put("itcDetail", filingService.getItcDetail(p.getAgency(), p.getPeriodStart(), p.getPeriodEnd()));
                var returnLines = filingService.getReturnLineBreakdown(p);
                body.put("returnLineBreakdown", returnLines);
                body.put("taxReturnRows", filingService.getTaxReturnRows(p, returnLines).stream()
                        .map(this::localizedReturnRow)
                        .toList());
                body.put("hasUnmappedReturnLineAmounts",
                        filingService.hasUnmappedReturnLineAmounts(p, returnLines));
                return ResponseEntity.ok(body);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> localizedReturnRow(TaxReturnRowDTO row) {
        Map<String, Object> localized = new HashMap<>();
        localized.put("description", messageSource.getMessage(
                row.descriptionKey(), null, LocaleContextHolder.getLocale()));
        localized.put("line", row.line());
        localized.put("amount", row.amount());
        localized.put("balance", row.balance());
        localized.put("total", row.total());
        localized.put("unmapped", row.unmapped());
        return localized;
    }
}
