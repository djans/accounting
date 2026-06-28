package com.cogitosum.controller;

import com.cogitosum.entity.BillPayment;
import com.cogitosum.entity.PaymentStatus;
import com.cogitosum.service.BillPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/bill-payments")
public class BillPaymentController {

    @Autowired
    private BillPaymentService billPaymentService;

    @PostMapping
    public ResponseEntity<BillPayment> recordPayment(@RequestBody BillPayment payment) {
        try {
            return new ResponseEntity<>(billPaymentService.recordPayment(payment), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping
    public ResponseEntity<List<BillPayment>> getAllPayments() {
        try {
            return new ResponseEntity<>(billPaymentService.getAllPayments(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillPayment> getPaymentById(@PathVariable Long id) {
        try {
            Optional<BillPayment> payment = billPaymentService.getPaymentById(id);
            return payment.map(p -> new ResponseEntity<>(p, HttpStatus.OK))
                    .orElseGet(() -> new ResponseEntity<>(null, HttpStatus.NOT_FOUND));
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/bill/{billId}")
    public ResponseEntity<List<BillPayment>> getPaymentsByBillId(@PathVariable Long billId) {
        try {
            return new ResponseEntity<>(billPaymentService.getPaymentsByBillId(billId), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<BillPayment> markPaymentAsCompleted(@PathVariable Long id) {
        try {
            BillPayment payment = billPaymentService.markPaymentAsCompleted(id);
            return payment != null
                    ? new ResponseEntity<>(payment, HttpStatus.OK)
                    : new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}/refund")
    public ResponseEntity<BillPayment> refundPayment(@PathVariable Long id) {
        try {
            BillPayment payment = billPaymentService.refundPayment(id);
            return payment != null
                    ? new ResponseEntity<>(payment, HttpStatus.OK)
                    : new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayment(@PathVariable Long id) {
        try {
            billPaymentService.deletePayment(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
