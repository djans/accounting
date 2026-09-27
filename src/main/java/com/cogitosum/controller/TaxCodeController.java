package com.cogitosum.controller;

import com.cogitosum.entity.TaxCode;
import com.cogitosum.service.TaxCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tax-codes")
public class TaxCodeController {

    @Autowired
    private TaxCodeService service;

    @GetMapping
    public List<TaxCode> list() {
        return service.getAllCodes();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaxCode> get(@PathVariable Long id) {
        return service.getCodeById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public TaxCode create(@RequestBody TaxCode code) {
        return service.createCode(code);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaxCode> update(@PathVariable Long id, @RequestBody TaxCode code) {
        TaxCode updated = service.updateCode(id, code);
        return updated == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteCode(id);
        return ResponseEntity.noContent().build();
    }
}
