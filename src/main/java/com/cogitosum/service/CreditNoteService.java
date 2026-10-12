package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteLineItem;
import com.cogitosum.entity.CreditNoteStatus;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.repository.CreditNoteRepository;
import com.cogitosum.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CreditNoteService {

    private final CreditNoteRepository creditNoteRepository;
    private final InvoiceRepository invoiceRepository;
    private final CreditNotePostingService postingService;
    private final CurrentCompanyContext companyContext;

    public CreditNoteService(    CreditNoteRepository creditNoteRepository,
    InvoiceRepository invoiceRepository,
    CreditNotePostingService postingService,
    CurrentCompanyContext companyContext) {
        this.creditNoteRepository = creditNoteRepository;
        this.invoiceRepository = invoiceRepository;
        this.postingService = postingService;
        this.companyContext = companyContext;
    }

    @Transactional(readOnly = true)
    public List<CreditNote> getAllCreditNotes() {
        return creditNoteRepository.findAllByCompanyIdOrderByCreditNoteDateDescIdDesc(
                companyContext.requireCompanyId());
    }

    @Transactional(readOnly = true)
    public List<CreditNote> getInvoiceCreditNotes(Long invoiceId) {
        return creditNoteRepository.findByCompanyIdAndInvoiceIdOrderByCreditNoteDateDescIdDesc(
                companyContext.requireCompanyId(), invoiceId);
    }

    @Transactional(readOnly = true)
    public CreditNote getCreditNote(Long id) {
        return creditNoteRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Credit note not found"));
    }

    @Transactional(readOnly = true)
    public BigDecimal getRemainingCreditableAmount(Invoice invoice) {
        BigDecimal issued = issuedTotal(invoice);
        return zero(invoice.getTotalAmount()).subtract(issued).max(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public boolean canCredit(Invoice invoice) {
        return invoice.getStatus() != null && invoice.getStatus() != InvoiceStatus.DRAFT
                && invoice.getStatus() != InvoiceStatus.CANCELLED
                && invoice.getStatus() != InvoiceStatus.REFUNDED;
    }

    @Transactional
    public CreditNote createDraft(Long invoiceId, String reason, LocalDate date,
                                  List<LineInput> lineInputs) {
        Long companyId = companyContext.requireCompanyId();
        Invoice invoice = invoiceRepository.findLockedByIdAndCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        validateSourceInvoice(invoice);
        String validReason = validateReason(reason);
        if (date == null) {
            throw new IllegalArgumentException("Credit note date is required");
        }
        validateCreditNoteDate(invoice, date);
        CreditNote note = new CreditNote();
        note.setCompany(companyContext.requireCompany());
        note.setInvoice(invoice);
        note.setCreditNoteNumber(suggestNextNumber(companyId));
        note.setCreditNoteDate(date);
        note.setReason(validReason);
        note.setStatus(CreditNoteStatus.DRAFT);
        note.setLineItems(createLines(note, lineInputs));
        calculateAmounts(note);
        validateAmount(note, getRemainingCreditableAmount(invoice));
        return creditNoteRepository.save(note);
    }

    @Transactional
    public CreditNote updateDraft(Long id, String reason, LocalDate date, List<LineInput> lineInputs) {
        Long companyId = companyContext.requireCompanyId();
        CreditNote note = creditNoteRepository.findLockedByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit note not found"));
        if (note.getStatus() != CreditNoteStatus.DRAFT) {
            throw new IllegalStateException("Only draft credit notes can be edited");
        }
        Invoice invoice = invoiceRepository.findLockedByIdAndCompanyId(note.getInvoice().getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        validateSourceInvoice(invoice);
        if (date == null) {
            throw new IllegalArgumentException("Credit note date is required");
        }
        validateCreditNoteDate(invoice, date);
        note.setInvoice(invoice);
        note.setReason(validateReason(reason));
        note.setCreditNoteDate(date);
        note.getLineItems().clear();
        note.getLineItems().addAll(createLines(note, lineInputs));
        calculateAmounts(note);
        validateAmount(note, getRemainingCreditableAmount(invoice));
        return creditNoteRepository.save(note);
    }

    @Transactional
    public CreditNote issue(Long id) {
        Long companyId = companyContext.requireCompanyId();
        CreditNote note = creditNoteRepository.findLockedByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit note not found"));
        if (note.getStatus() != CreditNoteStatus.DRAFT) {
            throw new IllegalStateException("Only draft credit notes can be issued");
        }
        Invoice invoice = invoiceRepository.findLockedByIdAndCompanyId(note.getInvoice().getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        validateSourceInvoice(invoice);
        note.setInvoice(invoice);
        validateAmount(note, getRemainingCreditableAmount(invoice));
        note.setStatus(CreditNoteStatus.ISSUED);
        note.setIssuedAt(LocalDateTime.now());
        CreditNote issued = creditNoteRepository.save(note);
        postingService.postCreditNote(issued);
        updateInvoiceStatus(invoice, issuedTotal(invoice));
        invoiceRepository.save(invoice);
        return issued;
    }

    @Transactional
    public void deleteDraft(Long id) {
        CreditNote note = creditNoteRepository.findLockedByIdAndCompanyId(id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Credit note not found"));
        if (note.getStatus() != CreditNoteStatus.DRAFT) {
            throw new IllegalStateException("Only draft credit notes can be deleted");
        }
        creditNoteRepository.delete(note);
    }

    private List<CreditNoteLineItem> createLines(CreditNote note, List<LineInput> lineInputs) {
        if (lineInputs == null || lineInputs.isEmpty()) {
            throw new IllegalArgumentException("At least one credit note line is required");
        }
        List<CreditNoteLineItem> lines = new ArrayList<>();
        int lineNumber = 1;
        for (LineInput input : lineInputs) {
            if (input == null || input.quantity() == null || input.quantity().signum() < 0
                    || input.unitPrice() == null || input.unitPrice().signum() < 0
                    || hasMoreThanTwoDecimalPlaces(input.quantity())
                    || hasMoreThanTwoDecimalPlaces(input.unitPrice())) {
                throw new IllegalArgumentException(
                        "Credit note line quantities and prices must be non-negative and use at most two decimals");
            }
            if (input.quantity().signum() == 0) {
                continue;
            }
            if (input.description() == null || input.description().isBlank()
                    || input.description().trim().length() > 500) {
                throw new IllegalArgumentException("Credit note lines need a description");
            }
            CreditNoteLineItem line = new CreditNoteLineItem();
            line.setCreditNote(note);
            line.setDescription(input.description().trim());
            line.setLineNumber(lineNumber++);
            line.setQuantity(input.quantity());
            line.setUnitPrice(input.unitPrice());
            lines.add(line);
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("At least one credit note line must have a positive quantity");
        }
        return lines;
    }

    private void calculateAmounts(CreditNote note) {
        BigDecimal subtotal = note.getLineItems().stream()
                .map(CreditNoteLineItem::calculateTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        Invoice invoice = note.getInvoice();
        note.setSubtotal(subtotal);
        note.setGstAmount(prorate(invoice.getGstAmount(), subtotal, invoice.getSubtotal()));
        note.setHstAmount(prorate(invoice.getHstAmount(), subtotal, invoice.getSubtotal()));
        note.setQstAmount(prorate(invoice.getQstAmount(), subtotal, invoice.getSubtotal()));
        note.setTotalAmount(subtotal.add(note.getGstAmount()).add(note.getHstAmount()).add(note.getQstAmount()));
    }

    private BigDecimal prorate(BigDecimal sourceTax, BigDecimal noteSubtotal, BigDecimal sourceSubtotal) {
        if (sourceTax == null || sourceSubtotal == null || sourceSubtotal.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return sourceTax.multiply(noteSubtotal).divide(sourceSubtotal, 2, RoundingMode.HALF_UP);
    }

    private void validateAmount(CreditNote note, BigDecimal remaining) {
        if (note.getTotalAmount() == null || note.getTotalAmount().signum() <= 0) {
            throw new IllegalArgumentException("Credit note total must be greater than zero");
        }
        if (note.getTotalAmount().compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Credit note total exceeds the remaining creditable invoice amount");
        }
    }

    private void validateSourceInvoice(Invoice invoice) {
        if (invoice.getStatus() == null || invoice.getStatus() == InvoiceStatus.DRAFT
                || invoice.getStatus() == InvoiceStatus.CANCELLED
                || invoice.getStatus() == InvoiceStatus.REFUNDED) {
            throw new IllegalStateException("Only issued, active invoices can be credited");
        }
    }

    private String validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 1000) {
            throw new IllegalArgumentException("A credit note reason of up to 1000 characters is required");
        }
        return reason.trim();
    }

    private void validateCreditNoteDate(Invoice invoice, LocalDate date) {
        if (invoice.getInvoiceDate() != null && date.isBefore(invoice.getInvoiceDate())) {
            throw new IllegalArgumentException("Credit note date cannot be before the invoice date");
        }
    }

    private boolean hasMoreThanTwoDecimalPlaces(BigDecimal amount) {
        return amount.stripTrailingZeros().scale() > 2;
    }

    private String suggestNextNumber(Long companyId) {
        return creditNoteRepository.findTopByCompanyIdOrderByIdDesc(companyId)
                .map(CreditNote::getCreditNoteNumber)
                .map(this::incrementNumber)
                .orElse("CN-001");
    }

    private String incrementNumber(String number) {
        Matcher matcher = Pattern.compile("^(.*?)(\\d+)$").matcher(Objects.requireNonNullElse(number, ""));
        if (!matcher.matches()) {
            return number + "-001";
        }
        long next = Long.parseLong(matcher.group(2)) + 1;
        return matcher.group(1) + String.format("%0" + matcher.group(2).length() + "d", next);
    }

    private BigDecimal issuedTotal(Invoice invoice) {
        if (invoice.getId() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = creditNoteRepository.sumTotalByCompanyIdAndInvoiceIdAndStatus(
                companyContext.requireCompanyId(), invoice.getId(), CreditNoteStatus.ISSUED);
        return zero(sum);
    }

    private void updateInvoiceStatus(Invoice invoice, BigDecimal totalCredits) {
        if (invoice.getStatus() == InvoiceStatus.CANCELLED || invoice.getStatus() == InvoiceStatus.DRAFT) {
            return;
        }
        BigDecimal paid = zero(invoice.getPaidAmount());
        BigDecimal balance = zero(invoice.getTotalAmount()).subtract(paid).subtract(totalCredits);
        if (balance.signum() <= 0) {
            invoice.setStatus(totalCredits.signum() > 0 ? InvoiceStatus.CREDITED : InvoiceStatus.PAID);
        } else if (paid.signum() > 0) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        } else if (invoice.getStatus() == InvoiceStatus.PAID
                || invoice.getStatus() == InvoiceStatus.PARTIALLY_PAID
                || invoice.getStatus() == InvoiceStatus.CREDITED
                || invoice.getStatus() == InvoiceStatus.REFUNDED) {
            invoice.setStatus(invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now())
                    ? InvoiceStatus.OVERDUE : InvoiceStatus.SENT);
        }
    }

    private BigDecimal zero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public record LineInput(String description, BigDecimal quantity, BigDecimal unitPrice) {
    }
}
