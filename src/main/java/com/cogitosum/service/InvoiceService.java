package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.LineItem;
import com.cogitosum.entity.TaxCode;
import com.cogitosum.entity.TaxGroup;
import com.cogitosum.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InvoiceService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoicePostingService invoicePostingService;

    @Autowired
    private TaxCodeService taxCodeService;
    
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
            invoice.setInvoiceNumber(suggestNextInvoiceNumber());
        }
        if (invoice.getInvoiceDate() == null) {
            invoice.setInvoiceDate(LocalDate.now());
        }
        if (invoice.getDueDate() == null) {
            invoice.setDueDate(LocalDate.now().plusDays(30));
        }
        calculateInvoiceTotals(invoice, regimeCode);
        invoice.setTaxRegime(regimeCode != null ? regimeCode : invoice.getCustomer().getProvince());

        Invoice saved = invoiceRepository.save(invoice);
        return saved;
    }

    public Invoice updateInvoice(Long id, Invoice invoice) {
        return updateInvoice(id, invoice, null);
    }

    @Transactional
    public Invoice updateInvoice(Long id, Invoice invoice, String regimeCode) {
        Optional<Invoice> existingInvoice = invoiceRepository.findById(id);
        if (existingInvoice.isPresent()) {
            Invoice inv = existingInvoice.get();
            if (!canEdit(inv)) {
                throw new IllegalStateException("Only draft invoices can be edited");
            }
            // A legacy draft may already have been posted. Reverse that entry before
            // replacing the draft so its old amounts do not remain in the ledger.
            invoicePostingService.reverseInvoice(inv, "Draft invoice edited");
            List<LineItem> updatedLineItems = invoice.getLineItems() == null
                    ? List.of()
                    : invoice.getLineItems();
            inv.getLineItems().clear();
            inv.setCustomer(invoice.getCustomer());
            inv.setInvoiceNumber(invoice.getInvoiceNumber());
            inv.setInvoiceDate(invoice.getInvoiceDate());
            inv.setDueDate(invoice.getDueDate());
            inv.setNotes(invoice.getNotes());
            inv.setTaxRegime(regimeCode != null ? regimeCode : inv.getTaxRegime());
            for (LineItem lineItem : updatedLineItems) {
                lineItem.setInvoice(inv);
                inv.getLineItems().add(lineItem);
            }
            calculateInvoiceTotals(inv, regimeCode);
            Invoice saved = invoiceRepository.save(inv);
            return saved;
        }
        return null;
    }

    public boolean canEdit(Invoice invoice) {
        return invoice.getStatus() == InvoiceStatus.DRAFT;
    }

    public String suggestNextInvoiceNumber() {
        return invoiceRepository.findTopByOrderByIdDesc()
            .map(Invoice::getInvoiceNumber)
            .map(this::incrementInvoiceNumber)
            .orElse("INV-001");
    }

    private String incrementInvoiceNumber(String number) {
        Matcher matcher = Pattern.compile("^(.*?)(\\d+)$").matcher(number == null ? "" : number);
        if (!matcher.matches()) return number + "-001";
        String prefix = matcher.group(1);
        String digits = matcher.group(2);
        long next = Long.parseLong(digits) + 1;
        return prefix + String.format("%0" + digits.length() + "d", next);
    }

    public Invoice duplicateInvoice(Long id) {
        Invoice source = invoiceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        Invoice copy = new Invoice();
        copy.setInvoiceNumber(suggestNextInvoiceNumber());
        copy.setCustomer(source.getCustomer());
        copy.setInvoiceDate(LocalDate.now());
        copy.setDueDate(source.getDueDate());
        copy.setStatus(InvoiceStatus.DRAFT);
        copy.setNotes(source.getNotes());
        copy.setTaxRegime(source.getTaxRegime());
        List<LineItem> items = source.getLineItems().stream().map(line -> {
            LineItem copyLine = new LineItem();
            copyLine.setInvoice(copy);
            copyLine.setDescription(line.getDescription());
            copyLine.setQuantity(line.getQuantity());
            copyLine.setUnitPrice(line.getUnitPrice());
            return copyLine;
        }).toList();
        copy.setLineItems(new java.util.ArrayList<>(items));
        calculateInvoiceTotals(copy, null);
        return invoiceRepository.save(copy);
    }
    public Optional<Invoice> getInvoiceById(Long id) {
        return invoiceRepository.findById(id);
    }
    
    public Optional<Invoice> getInvoiceByInvoiceNumber(String invoiceNumber) {
        return invoiceRepository.findByInvoiceNumber(invoiceNumber);
    }
    
    public List<Invoice> getInvoicesByCustomerId(Long customerId) {
        return invoiceRepository.findByCustomerIdOrderByInvoiceNumberDesc(customerId);
    }

    public List<Invoice> getInvoicesByStatus(InvoiceStatus status) {
        return invoiceRepository.findByStatusOrderByInvoiceNumberDesc(status);
    }

    public List<Invoice> getInvoicesByDateRange(LocalDate startDate, LocalDate endDate) {
        return invoiceRepository.findByInvoiceDateBetweenOrderByInvoiceDateDesc(startDate, endDate);
    }

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAllByOrderByInvoiceNumberDesc();
    }
    
    @Transactional
    public Invoice markInvoiceAsSent(Long id) {
        Optional<Invoice> invoice = invoiceRepository.findById(id);
        if (invoice.isPresent()) {
            Invoice inv = invoice.get();
            if (inv.getStatus() != InvoiceStatus.DRAFT) {
                throw new IllegalStateException("Only draft invoices can be sent");
            }
            if (invoicePostingService.hasPostedJournal(inv)) {
                invoicePostingService.reverseInvoice(inv, "Reposting invoice");
            }
            inv.setStatus(InvoiceStatus.SENT);
            Invoice saved = invoiceRepository.save(inv);
            invoicePostingService.postInvoice(saved);
            return saved;
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
        List<Invoice> invoices = invoiceRepository.findByStatusOrderByInvoiceNumberDesc(InvoiceStatus.SENT);
        return invoices.stream()
            .filter(inv -> inv.getDueDate().isBefore(LocalDate.now()))
            .toList();
    }
    
    void calculateInvoiceTotals(Invoice invoice) {
        calculateInvoiceTotals(invoice, null);
    }

    void calculateInvoiceTotals(Invoice invoice, String regimeCode) {
        String province = invoice.getCustomer() != null ? invoice.getCustomer().getProvince() : null;
        String effectiveCode = regimeCode != null ? regimeCode : (province != null ? province : "FED");
        
        Optional<TaxGroup> groupOpt = taxCodeService.getAllGroups().stream()
                .filter(g -> g.getCode().equals(effectiveCode))
                .findFirst();

        BigDecimal subtotal = invoice.getLineItems().stream()
                .map(LineItem::calculateTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.setSubtotal(subtotal);

        BigDecimal gst = BigDecimal.ZERO;
        BigDecimal hst = BigDecimal.ZERO;
        BigDecimal qst = BigDecimal.ZERO;

        if (groupOpt.isPresent()) {
            for (TaxCode item : groupOpt.get().getTaxItems()) {
                if (Boolean.FALSE.equals(item.getForSales())) continue;
                
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
            TaxRegime regime = resolveRegime(province, regimeCode);
            gst = subtotal.multiply(regime.gstRate()).setScale(2, java.math.RoundingMode.HALF_UP);
            hst = subtotal.multiply(regime.hstRate()).setScale(2, java.math.RoundingMode.HALF_UP);
            qst = subtotal.multiply(regime.qstRate()).setScale(2, java.math.RoundingMode.HALF_UP);
        }

        invoice.setGstAmount(gst);
        invoice.setHstAmount(hst);
        invoice.setQstAmount(qst);
        invoice.setTotalAmount(subtotal.add(gst).add(hst).add(qst));
    }

    public String effectiveTaxRegime(Invoice invoice) {
        if (invoice.getTaxRegime() != null && !invoice.getTaxRegime().isBlank()) {
            return invoice.getTaxRegime();
        }
        if (invoice.getCustomer() == null || invoice.getCustomer().getProvince() == null) {
            return "";
        }
        String province = invoice.getCustomer().getProvince();
        return switch (province) {
            case "ON" -> "ON";
            case "NS", "NB", "NL", "PE" -> "MARITIME";
            case "QC" -> "QC";
            default -> "FED";
        };
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
    
    public List<Invoice> getUnpaidInvoicesByCustomerId(Long customerId) {
        return invoiceRepository.findByCustomerIdOrderByInvoiceNumberDesc(customerId).stream()
                .filter(inv -> inv.getStatus() != InvoiceStatus.PAID 
                            && inv.getStatus() != InvoiceStatus.CANCELLED
                            && inv.getStatus() != InvoiceStatus.DRAFT
                            && inv.getStatus() != InvoiceStatus.REFUNDED)
                .collect(java.util.stream.Collectors.toList());
    }

    public java.math.BigDecimal getCustomerBalance(Long customerId) {
        return getUnpaidInvoicesByCustomerId(customerId).stream()
                .map(inv -> inv.getTotalAmount().subtract(inv.getPaidAmount()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    public void deleteInvoice(Long id) {
        Optional<Invoice> invoice = invoiceRepository.findById(id);
        invoice.ifPresent(inv -> invoicePostingService.reverseInvoice(inv, "Invoice deleted"));
        invoiceRepository.deleteById(id);
    }
}
