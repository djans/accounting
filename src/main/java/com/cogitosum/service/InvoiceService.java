package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.LineItem;
import com.cogitosum.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InvoiceService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoicePostingService invoicePostingService;
    
    // Canadian tax rates - default federal GST when no province match
    private static final BigDecimal GST_RATE = new BigDecimal("0.05");      // 5% TPS
    private static final BigDecimal HST_RATE = new BigDecimal("0.00");      // HST provinces only
    private static final BigDecimal QST_RATE = new BigDecimal("0.09975");   // 9.975% TVQ (Quebec)

    public static final List<TaxRegime> REGIMES = List.of(
        new TaxRegime("QC", "Québec (TPS 5% + TVQ 9,975%)", GST_RATE, BigDecimal.ZERO, QST_RATE),
        new TaxRegime("ON", "Ontario (HST 13%)", BigDecimal.ZERO, new BigDecimal("0.13"), BigDecimal.ZERO),
        new TaxRegime("MARITIME", "Maritimes (HST 15%)", BigDecimal.ZERO, new BigDecimal("0.15"), BigDecimal.ZERO),
        new TaxRegime("FED", "Fédéral seulement (TPS 5%)", GST_RATE, BigDecimal.ZERO, BigDecimal.ZERO),
        new TaxRegime("EXEMPT", "Hors taxe / Exonéré", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    );

    public List<TaxRegime> getRegimes() { return REGIMES; }

    public TaxRegime findRegime(String code) {
        if (code == null) return null;
        return REGIMES.stream().filter(r -> r.code().equals(code)).findFirst().orElse(null);
    }
    
    public Invoice createInvoice(Invoice invoice) {
        return createInvoice(invoice, null);
    }

    public Invoice createInvoice(Invoice invoice, String regimeCode) {
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isEmpty()) {
            invoice.setInvoiceNumber("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (invoice.getInvoiceDate() == null) {
            invoice.setInvoiceDate(LocalDate.now());
        }
        if (invoice.getDueDate() == null) {
            invoice.setDueDate(LocalDate.now().plusDays(30));
        }
        calculateInvoiceTotals(invoice, regimeCode);

        Invoice saved = invoiceRepository.save(invoice);
        invoicePostingService.postInvoice(saved);
        return saved;
    }

    public Invoice updateInvoice(Long id, Invoice invoice) {
        return updateInvoice(id, invoice, null);
    }

    public Invoice updateInvoice(Long id, Invoice invoice, String regimeCode) {
        Optional<Invoice> existingInvoice = invoiceRepository.findById(id);
        if (existingInvoice.isPresent()) {
            Invoice inv = existingInvoice.get();
            inv.setCustomer(invoice.getCustomer());
            inv.setInvoiceDate(invoice.getInvoiceDate());
            inv.setDueDate(invoice.getDueDate());
            inv.setNotes(invoice.getNotes());
            inv.setLineItems(invoice.getLineItems());
            calculateInvoiceTotals(inv, regimeCode);
            Invoice saved = invoiceRepository.save(inv);
            invoicePostingService.repostInvoice(saved);
            return saved;
        }
        return null;
    }
    
    public Optional<Invoice> getInvoiceById(Long id) {
        return invoiceRepository.findById(id);
    }
    
    public Optional<Invoice> getInvoiceByInvoiceNumber(String invoiceNumber) {
        return invoiceRepository.findByInvoiceNumber(invoiceNumber);
    }
    
    public List<Invoice> getInvoicesByCustomerId(Long customerId) {
        return invoiceRepository.findByCustomerId(customerId);
    }
    
    public List<Invoice> getInvoicesByStatus(InvoiceStatus status) {
        return invoiceRepository.findByStatus(status);
    }
    
    public List<Invoice> getInvoicesByDateRange(LocalDate startDate, LocalDate endDate) {
        return invoiceRepository.findByInvoiceDateBetween(startDate, endDate);
    }
    
    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }
    
    public Invoice markInvoiceAsSent(Long id) {
        Optional<Invoice> invoice = invoiceRepository.findById(id);
        if (invoice.isPresent()) {
            invoice.get().setStatus(InvoiceStatus.SENT);
            return invoiceRepository.save(invoice.get());
        }
        return null;
    }
    
    public Invoice markInvoiceAsViewed(Long id) {
        Optional<Invoice> invoice = invoiceRepository.findById(id);
        if (invoice.isPresent()) {
            invoice.get().setStatus(InvoiceStatus.VIEWED);
            return invoiceRepository.save(invoice.get());
        }
        return null;
    }
    
    public Invoice cancelInvoice(Long id) {
        Optional<Invoice> invoice = invoiceRepository.findById(id);
        if (invoice.isPresent()) {
            Invoice inv = invoice.get();
            invoicePostingService.reverseInvoice(inv, "Invoice cancelled");
            inv.setStatus(InvoiceStatus.CANCELLED);
            return invoiceRepository.save(inv);
        }
        return null;
    }
    
    public List<Invoice> getOverdueInvoices() {
        List<Invoice> invoices = invoiceRepository.findByStatus(InvoiceStatus.SENT);
        return invoices.stream()
            .filter(inv -> inv.getDueDate().isBefore(LocalDate.now()))
            .toList();
    }
    
    void calculateInvoiceTotals(Invoice invoice) {
        calculateInvoiceTotals(invoice, null);
    }

    void calculateInvoiceTotals(Invoice invoice, String regimeCode) {
        String province = invoice.getCustomer() != null ? invoice.getCustomer().getProvince() : null;
        TaxRegime regime = resolveRegime(province, regimeCode);
        invoice.calculateTotals(regime.gstRate(), regime.hstRate(), regime.qstRate());
    }

    /**
     * Resolves the effective tax rates: an explicit regime code wins, otherwise rates are
     * derived from the province. Shared with the purchase side (bills) so a single source
     * of truth governs Canadian sales-tax rates.
     */
    public TaxRegime resolveRegime(String province, String regimeCode) {
        TaxRegime regime = findRegime(regimeCode);
        if (regime != null) {
            return regime;
        }
        return new TaxRegime(province == null ? "AUTO" : province, "Auto — " + province,
            gstRateFor(province), hstRateFor(province), qstRateFor(province));
    }

    private BigDecimal gstRateFor(String province) {
        if (province == null) return GST_RATE;
        // HST provinces roll GST into HST, so the GST portion is zero.
        return switch (province) {
            case "ON", "NS", "NB", "NL", "PE" -> BigDecimal.ZERO;
            default -> GST_RATE;
        };
    }

    private BigDecimal hstRateFor(String province) {
        if (province == null) return HST_RATE;
        return switch (province) {
            case "ON" -> new BigDecimal("0.13");
            case "NS", "NB", "NL", "PE" -> new BigDecimal("0.15");
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal qstRateFor(String province) {
        return "QC".equals(province) ? QST_RATE : BigDecimal.ZERO;
    }
    
    public void deleteInvoice(Long id) {
        Optional<Invoice> invoice = invoiceRepository.findById(id);
        invoice.ifPresent(inv -> invoicePostingService.reverseInvoice(inv, "Invoice deleted"));
        invoiceRepository.deleteById(id);
    }
}

