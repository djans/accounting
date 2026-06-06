package com.cogitosum.controller;

import com.cogitosum.entity.TaxAgency;
import com.cogitosum.service.TaxAgencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tax-agencies")
public class TaxAgencyController {

    @Autowired
    private TaxAgencyService service;

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
}
