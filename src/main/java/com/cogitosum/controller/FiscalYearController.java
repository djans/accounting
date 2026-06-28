package com.cogitosum.controller;

import com.cogitosum.entity.FiscalYear;
import com.cogitosum.service.FiscalYearCloseService;
import com.cogitosum.service.FiscalYearService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/fiscal-years")
public class FiscalYearController {

    @Autowired
    private FiscalYearService fiscalYearService;

    @Autowired
    private FiscalYearCloseService fiscalYearCloseService;

    @PostMapping
    public ResponseEntity<FiscalYear> createFiscalYear(@RequestBody FiscalYear fiscalYear) {
        try {
            return new ResponseEntity<>(fiscalYearService.createFiscalYear(fiscalYear), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping
    public ResponseEntity<List<FiscalYear>> getAllFiscalYears() {
        try {
            return new ResponseEntity<>(fiscalYearService.getAll(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<FiscalYear> getFiscalYearById(@PathVariable Long id) {
        try {
            Optional<FiscalYear> fy = fiscalYearService.getById(id);
            return fy.map(f -> new ResponseEntity<>(f, HttpStatus.OK))
                    .orElseGet(() -> new ResponseEntity<>(null, HttpStatus.NOT_FOUND));
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}/close")
    public ResponseEntity<FiscalYear> close(@PathVariable Long id,
                                            @RequestParam(defaultValue = "api") String postedBy) {
        try {
            return new ResponseEntity<>(fiscalYearCloseService.close(id, postedBy), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/{id}/reopen")
    public ResponseEntity<FiscalYear> reopen(@PathVariable Long id) {
        try {
            return new ResponseEntity<>(fiscalYearCloseService.reopen(id), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFiscalYear(@PathVariable Long id) {
        try {
            fiscalYearService.deleteFiscalYear(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
