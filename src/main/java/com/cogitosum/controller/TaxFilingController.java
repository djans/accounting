package com.cogitosum.controller;

import com.cogitosum.entity.TaxFilingPeriod;
import com.cogitosum.service.TaxFilingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tax-filings")
public class TaxFilingController {

    @Autowired
    private TaxFilingService service;

    @GetMapping
    public List<TaxFilingPeriod> list(@RequestParam(required = false) Long agencyId) {
        return agencyId == null ? service.getAll() : service.getByAgency(agencyId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaxFilingPeriod> get(@PathVariable Long id) {
        return service.getById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public TaxFilingPeriod create(@RequestBody Map<String, Object> body) {
        Long agencyId = ((Number) body.get("agencyId")).longValue();
        LocalDate start = LocalDate.parse((String) body.get("periodStart"));
        LocalDate end = LocalDate.parse((String) body.get("periodEnd"));
        return service.createPeriod(agencyId, start, end);
    }

    @PostMapping("/{id}/calculate")
    public TaxFilingPeriod calculate(@PathVariable Long id) {
        return service.calculate(id);
    }

    @PostMapping("/{id}/file")
    public TaxFilingPeriod file(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal itc = body.containsKey("itcAmount")
            ? new BigDecimal(body.get("itcAmount").toString())
            : BigDecimal.ZERO;
        String postedBy = (String) body.getOrDefault("postedBy", "api");
        return service.file(id, itc, postedBy);
    }

    @PostMapping("/{id}/pay")
    public TaxFilingPeriod pay(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String bankAccountNumber = (String) body.get("bankAccountNumber");
        LocalDate date = body.containsKey("paymentDate") ? LocalDate.parse((String) body.get("paymentDate")) : null;
        String postedBy = (String) body.getOrDefault("postedBy", "api");
        return service.recordPayment(id, bankAccountNumber, date, postedBy);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
