package com.cogitosum.service;

import com.cogitosum.entity.Bill;
import com.cogitosum.entity.BillLineItem;
import com.cogitosum.entity.BillStatus;
import com.cogitosum.entity.TaxCode;
import com.cogitosum.entity.TaxGroup;
import com.cogitosum.repository.BillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gère les factures d'achat (dépenses fournisseurs) — symétrique de {@link InvoiceService}.
 * Réutilise {@link InvoiceService#resolveRegime} pour appliquer les mêmes taux de taxe.
 */
@Service
public class BillService {

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillPostingService billPostingService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private TaxCodeService taxCodeService;

    public List<TaxRegime> getRegimes() {
        return invoiceService.getRegimes();
    }

    public Bill createBill(Bill bill) {
        return createBill(bill, null);
    }

    public Bill createBill(Bill bill, String regimeCode) {
        if (bill.getBillNumber() == null || bill.getBillNumber().isEmpty()) {
            bill.setBillNumber("BILL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (bill.getBillDate() == null) {
            bill.setBillDate(LocalDate.now());
        }
        if (bill.getDueDate() == null) {
            bill.setDueDate(LocalDate.now().plusDays(30));
        }
        calculateBillTotals(bill, regimeCode);

        Bill saved = billRepository.save(bill);
        billPostingService.postBill(saved);
        return saved;
    }

    public Bill updateBill(Long id, Bill bill) {
        return updateBill(id, bill, null);
    }

    public Bill updateBill(Long id, Bill bill, String regimeCode) {
        Optional<Bill> existingBill = billRepository.findById(id);
        if (existingBill.isPresent()) {
            Bill b = existingBill.get();
            b.setVendor(bill.getVendor());
            b.setBillDate(bill.getBillDate());
            b.setDueDate(bill.getDueDate());
            b.setNotes(bill.getNotes());
            b.setLineItems(bill.getLineItems());
            calculateBillTotals(b, regimeCode);
            Bill saved = billRepository.save(b);
            billPostingService.repostBill(saved);
            return saved;
        }
        return null;
    }

    public Optional<Bill> getBillById(Long id) {
        return billRepository.findById(id);
    }

    public Optional<Bill> getBillByBillNumber(String billNumber) {
        return billRepository.findByBillNumber(billNumber);
    }

    public List<Bill> getBillsByVendorId(Long vendorId) {
        return billRepository.findByVendorIdOrderByBillNumberDesc(vendorId);
    }

    public List<Bill> getBillsByStatus(BillStatus status) {
        return billRepository.findByStatusOrderByBillNumberDesc(status);
    }

    public List<Bill> getBillsByDateRange(LocalDate startDate, LocalDate endDate) {
        return billRepository.findByBillDateBetweenOrderByBillDateDesc(startDate, endDate);
    }

    public List<Bill> getAllBills() {
        return billRepository.findAllByOrderByBillNumberDesc();
    }

    public Bill markBillAsReceived(Long id) {
        Optional<Bill> bill = billRepository.findById(id);
        if (bill.isPresent()) {
            bill.get().setStatus(BillStatus.RECEIVED);
            return billRepository.save(bill.get());
        }
        return null;
    }

    public Bill cancelBill(Long id) {
        Optional<Bill> bill = billRepository.findById(id);
        if (bill.isPresent()) {
            Bill b = bill.get();
            billPostingService.reverseBill(b, "Bill cancelled");
            b.setStatus(BillStatus.CANCELLED);
            return billRepository.save(b);
        }
        return null;
    }

    public List<Bill> getOverdueBills() {
        List<Bill> bills = billRepository.findByStatusOrderByBillNumberDesc(BillStatus.RECEIVED);
        return bills.stream()
            .filter(b -> b.getDueDate().isBefore(LocalDate.now()))
            .toList();
    }

    void calculateBillTotals(Bill bill, String regimeCode) {
        String province = bill.getVendor() != null ? bill.getVendor().getProvince() : null;
        String effectiveCode = regimeCode != null ? regimeCode : (province != null ? province : "FED");

        Optional<TaxGroup> groupOpt = taxCodeService.getAllGroups().stream()
                .filter(g -> g.getCode().equals(effectiveCode))
                .findFirst();

        BigDecimal subtotal = bill.getLineItems().stream()
                .map(BillLineItem::calculateTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        bill.setSubtotal(subtotal);

        BigDecimal gst = BigDecimal.ZERO;
        BigDecimal hst = BigDecimal.ZERO;
        BigDecimal qst = BigDecimal.ZERO;

        if (groupOpt.isPresent()) {
            for (TaxCode item : groupOpt.get().getTaxItems()) {
                if (Boolean.FALSE.equals(item.getForPurchases())) continue;

                BigDecimal taxAmount = subtotal.multiply(item.getRate()).setScale(2, java.math.RoundingMode.HALF_UP);
                String code = item.getCode();
                if (code.contains("TPS") || code.contains("GST")) {
                    gst = gst.add(taxAmount);
                } else if (code.contains("HST")) {
                    hst = hst.add(taxAmount);
                } else if (code.contains("TVQ") || code.contains("QST")) {
                    qst = qst.add(taxAmount);
                }
            }
        } else {
            // Fallback to legacy static rates if no group found
            TaxRegime regime = invoiceService.resolveRegime(province, regimeCode);
            gst = subtotal.multiply(regime.gstRate()).setScale(2, java.math.RoundingMode.HALF_UP);
            hst = subtotal.multiply(regime.hstRate()).setScale(2, java.math.RoundingMode.HALF_UP);
            qst = subtotal.multiply(regime.qstRate()).setScale(2, java.math.RoundingMode.HALF_UP);
        }

        bill.setGstAmount(gst);
        bill.setHstAmount(hst);
        bill.setQstAmount(qst);
        bill.setTotalAmount(subtotal.add(gst).add(hst).add(qst));
    }

    public void deleteBill(Long id) {
        Optional<Bill> bill = billRepository.findById(id);
        bill.ifPresent(b -> billPostingService.reverseBill(b, "Bill deleted"));
        billRepository.deleteById(id);
    }
}
