package com.cogitosum.controller;

import com.cogitosum.service.BillingReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private BillingReportService billingReportService;

    @GetMapping("/revenue")
    public ResponseEntity<Map<String, ?>> getRevenueReport(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        try {
            Map<String, ?> report = billingReportService.getRevenueReport(startDate, endDate);
            return new ResponseEntity<>(report, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/aging")
    public ResponseEntity<Map<String, Object>> getAgingAnalysis() {
        try {
            Map<String, Object> report = billingReportService.getAgingAnalysis();
            return new ResponseEntity<>(report, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/invoice-status-summary")
    public ResponseEntity<Map<String, Object>> getInvoiceStatusSummary() {
        try {
            Map<String, Object> summary = billingReportService.getInvoiceStatusSummary();
            return new ResponseEntity<>(summary, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/tax-summary")
    public ResponseEntity<Map<String, ?>> getTaxSummary(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        try {
            Map<String, ?> report = billingReportService.getTaxSummary(startDate, endDate);
            return new ResponseEntity<>(report, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

