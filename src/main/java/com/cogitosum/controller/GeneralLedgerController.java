package com.cogitosum.controller;

import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.service.GeneralLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/general-ledger")
public class GeneralLedgerController {

    @Autowired
    private GeneralLedgerService generalLedgerService;

    @GetMapping
    public ResponseEntity<List<GeneralLedger>> getAllLedgerAccounts() {
        try {
            List<GeneralLedger> ledgers = generalLedgerService.getAllLedgerAccounts();
            return new ResponseEntity<>(ledgers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<GeneralLedger> getLedgerByAccountId(@PathVariable Long accountId) {
        try {
            Optional<GeneralLedger> ledger = generalLedgerService.getLedgerByAccountId(accountId);
            return ledger.map(gl -> new ResponseEntity<>(gl, HttpStatus.OK))
                    .orElseGet(() -> new ResponseEntity<>(null, HttpStatus.NOT_FOUND));
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

