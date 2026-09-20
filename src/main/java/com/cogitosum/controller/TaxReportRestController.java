package com.cogitosum.controller;

import com.cogitosum.entity.TaxAgency;
import com.cogitosum.entity.TaxCode;
import com.cogitosum.service.TaxAgencyService;
import com.cogitosum.service.TaxCodeService;
import com.cogitosum.service.TaxFilingService;
import com.cogitosum.repository.GeneralLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    @GetMapping("/reconciliation")
    public ResponseEntity<List<Map<String, Object>>> reconciliation() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TaxAgency agency : agencyService.getAll()) {
            for (TaxCode code : codeService.getByAgency(agency.getId())) {
                if (code.getPayableAccount() == null) continue;
                Map<String, Object> row = new HashMap<>();
                row.put("agency", agency);
                row.put("code", code);
                row.put("account", code.getPayableAccount());
                BigDecimal balance = ledgerRepository.findByAccountId(code.getPayableAccount().getId())
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
        return ResponseEntity.ok(codeService.getAll());
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
                return ResponseEntity.ok(body);
            })
            .orElse(ResponseEntity.notFound().build());
    }
}

