package com.cogitosum.controller;

import com.cogitosum.entity.TaxAgency;
import com.cogitosum.service.TaxAgencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tax-agencies")
public class TaxAgencyController {

    @Autowired
    private TaxAgencyService service;

    @Autowired
    private com.cogitosum.service.TaxFilingService filingService;

    @GetMapping
    public List<TaxAgency> list() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaxAgency> get(@PathVariable Long id) {
        return service.getById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public TaxAgency create(@RequestBody TaxAgency agency) {
        return service.createAgency(agency);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaxAgency> update(@PathVariable Long id, @RequestBody TaxAgency agency) {
        TaxAgency updated = service.updateAgency(id, agency);
        return updated == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteAgency(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<Map<String, Object>> getAgencyReport(@PathVariable Long id) {
        try {
            java.util.List<com.cogitosum.entity.TaxFilingPeriod> periods = filingService.getByAgency(id);
            java.math.BigDecimal totalCollected = periods.stream()
                .map(p -> p.getTaxCollected() == null ? java.math.BigDecimal.ZERO : p.getTaxCollected())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal totalItc = periods.stream()
                .map(p -> p.getTaxITC() == null ? java.math.BigDecimal.ZERO : p.getTaxITC())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            java.math.BigDecimal totalNet = periods.stream()
                .map(p -> p.getNetOwing() == null ? java.math.BigDecimal.ZERO : p.getNetOwing())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("periods", periods);
            map.put("totalCollected", totalCollected);
            map.put("totalItc", totalItc);
            map.put("totalNet", totalNet);
            return ResponseEntity.ok(map);
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
