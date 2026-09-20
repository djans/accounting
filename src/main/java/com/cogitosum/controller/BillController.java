package com.cogitosum.controller;

import com.cogitosum.entity.Bill;
import com.cogitosum.entity.BillStatus;
import com.cogitosum.service.BillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/bills")
public class BillController {

    @Autowired
    private BillService billService;

    @PostMapping
    public ResponseEntity<Bill> createBill(@RequestBody Bill bill) {
        try {
            return new ResponseEntity<>(billService.createBill(bill), HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Bill>> getAllBills() {
        try {
            return new ResponseEntity<>(billService.getAllBills(), HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Bill> getBillById(@PathVariable Long id) {
        try {
            Optional<Bill> bill = billService.getBillById(id);
            return bill.map(b -> new ResponseEntity<>(b, HttpStatus.OK))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/vendor/{vendorId}")
    public ResponseEntity<List<Bill>> getBillsByVendorId(@PathVariable Long vendorId) {
        try {
            return new ResponseEntity<>(billService.getBillsByVendorId(vendorId), HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Bill>> getBillsByStatus(@PathVariable BillStatus status) {
        try {
            return new ResponseEntity<>(billService.getBillsByStatus(status), HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<Bill>> getBillsByDateRange(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        try {
            return new ResponseEntity<>(billService.getBillsByDateRange(startDate, endDate), HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<Bill>> getOverdueBills() {
        try {
            return new ResponseEntity<>(billService.getOverdueBills(), HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Bill> updateBill(@PathVariable Long id, @RequestBody Bill bill) {
        try {
            Bill updated = billService.updateBill(id, bill);
            return updated != null
                    ? new ResponseEntity<>(updated, HttpStatus.OK)
                    : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/{id}/receive")
    public ResponseEntity<Bill> markBillAsReceived(@PathVariable Long id) {
        try {
            Bill bill = billService.markBillAsReceived(id);
            return bill != null
                    ? new ResponseEntity<>(bill, HttpStatus.OK)
                    : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Bill> cancelBill(@PathVariable Long id) {
        try {
            Bill bill = billService.cancelBill(id);
            return bill != null
                    ? new ResponseEntity<>(bill, HttpStatus.OK)
                    : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBill(@PathVariable Long id) {
        try {
            billService.deleteBill(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
