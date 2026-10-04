package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.BillRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.VendorRepository;
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

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private CurrentCompanyContext companyContext;

    public List<TaxRegime> getRegimes() {
        return invoiceService.getRegimes();
    }

    public Bill createBill(Bill bill) {
        return createBill(bill, null);
    }

    public Bill createBill(Bill bill, String regimeCode) {
        Long companyId = companyContext.requireCompanyId();
        bill.setCompany(companyContext.requireCompany());
        bill.setVendor(vendorRepository.findByIdAndCompanyId(requiredId(bill.getVendor()), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found")));
        resolveExpenseAccounts(bill, companyId);
        if (bill.getBillNumber() == null || bill.getBillNumber().isEmpty()) {
            bill.setBillNumber("BILL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (bill.getBillDate() == null) {
            bill.setBillDate(LocalDate.now());
        }
        if (bill.getDueDate() == null) {
            bill.setDueDate(LocalDate.now().plusDays(30));
        }
        String effectiveCode = effectiveRegimeCode(bill, regimeCode);
        bill.setTaxRegime(effectiveCode);
        calculateBillTotals(bill, effectiveCode);

        Bill saved = billRepository.save(bill);
        billPostingService.postBill(saved);
        return saved;
    }

    public Bill updateBill(Long id, Bill bill) {
        return updateBill(id, bill, null);
    }

    public Bill updateBill(Long id, Bill bill, String regimeCode) {
        Long companyId = companyContext.requireCompanyId();
        Optional<Bill> existingBill = billRepository.findByIdAndCompanyId(id, companyId);
        if (existingBill.isPresent()) {
            Bill b = existingBill.get();
            b.setVendor(vendorRepository.findByIdAndCompanyId(requiredId(bill.getVendor()), companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Vendor not found")));
            b.setBillDate(bill.getBillDate());
            b.setDueDate(bill.getDueDate());
            b.setNotes(bill.getNotes());
            resolveExpenseAccounts(bill, companyId);
            b.setLineItems(bill.getLineItems());
            String effectiveCode = effectiveRegimeCode(b, regimeCode);
            b.setTaxRegime(effectiveCode);
            calculateBillTotals(b, effectiveCode);
            Bill saved = billRepository.save(b);
            billPostingService.repostBill(saved);
            return saved;
        }
        return null;
    }

    public Optional<Bill> getBillById(Long id) {
        return billRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public Optional<Bill> getBillByBillNumber(String billNumber) {
        return billRepository.findByCompanyIdAndBillNumber(companyContext.requireCompanyId(), billNumber);
    }

    public List<Bill> getBillsByVendorId(Long vendorId) {
        return billRepository.findByCompanyIdAndVendorIdOrderByBillNumberDesc(companyContext.requireCompanyId(), vendorId);
    }

    public List<Bill> getBillsByStatus(BillStatus status) {
        return billRepository.findByCompanyIdAndStatusOrderByBillNumberDesc(companyContext.requireCompanyId(), status);
    }

    public List<Bill> getBillsByDateRange(LocalDate startDate, LocalDate endDate) {
        return billRepository.findByCompanyIdAndBillDateBetweenOrderByBillDateDesc(
                companyContext.requireCompanyId(), startDate, endDate);
    }

    public List<Bill> getAllBills() {
        return billRepository.findAllByCompanyIdOrderByBillNumberDesc(companyContext.requireCompanyId());
    }

    public Bill markBillAsReceived(Long id) {
        Optional<Bill> bill = billRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
        if (bill.isPresent()) {
            bill.get().setStatus(BillStatus.RECEIVED);
            return billRepository.save(bill.get());
        }
        return null;
    }

    public Bill cancelBill(Long id) {
        Optional<Bill> bill = billRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
        if (bill.isPresent()) {
            Bill b = bill.get();
            billPostingService.reverseBill(b, "Bill cancelled");
            b.setStatus(BillStatus.CANCELLED);
            return billRepository.save(b);
        }
        return null;
    }

    public List<Bill> getOverdueBills() {
        List<Bill> bills = billRepository.findByCompanyIdAndStatusOrderByBillNumberDesc(
                companyContext.requireCompanyId(), BillStatus.RECEIVED);
        return bills.stream()
            .filter(b -> b.getDueDate().isBefore(LocalDate.now()))
            .toList();
    }

    void calculateBillTotals(Bill bill, String regimeCode) {
        String province = bill.getVendor() != null ? bill.getVendor().getProvince() : null;
        String effectiveCode = regimeCode != null ? regimeCode : (province != null ? province : "FED");

        Optional<TaxCode> taxCodeOpt = taxCodeService.getAllCodes().stream()
                .filter(c -> c.getCode().equals(effectiveCode))
                .findFirst();

        BigDecimal subtotal = bill.getLineItems().stream()
                .map(BillLineItem::calculateTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        bill.setSubtotal(subtotal);

        BigDecimal gst = BigDecimal.ZERO;
        BigDecimal hst = BigDecimal.ZERO;
        BigDecimal qst = BigDecimal.ZERO;

        if (taxCodeOpt.isPresent() && taxCodeOpt.get().getPurchaseTaxGroup() != null) {
            for (TaxItem item : taxCodeOpt.get().getPurchaseTaxGroup().getTaxItems()) {
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
            // Fallback to legacy static rates if no tax code/group found
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

    private String effectiveRegimeCode(Bill bill, String regimeCode) {
        if (regimeCode != null && !regimeCode.isBlank()) {
            return regimeCode;
        }
        if (bill.getTaxRegime() != null && !bill.getTaxRegime().isBlank()) {
            return bill.getTaxRegime();
        }
        String province = bill.getVendor() != null ? bill.getVendor().getProvince() : null;
        return province != null && !province.isBlank() ? province : "FED";
    }

    public void deleteBill(Long id) {
        Optional<Bill> bill = billRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
        bill.ifPresent(b -> billPostingService.reverseBill(b, "Bill deleted"));
        bill.ifPresent(billRepository::delete);
    }

    private Long requiredId(Vendor vendor) {
        if (vendor == null || vendor.getId() == null) {
            throw new IllegalArgumentException("A vendor is required");
        }
        return vendor.getId();
    }

    private void resolveExpenseAccounts(Bill bill, Long companyId) {
        if (bill.getLineItems() == null) {
            return;
        }
        for (BillLineItem line : bill.getLineItems()) {
            if (line.getExpenseAccount() != null) {
                Long accountId = line.getExpenseAccount().getId();
                if (accountId == null) {
                    throw new IllegalArgumentException("Expense account is invalid");
                }
                line.setExpenseAccount(accountRepository.findByIdAndCompanyId(accountId, companyId)
                        .orElseThrow(() -> new IllegalArgumentException("Expense account not found")));
            }
        }
    }
}
