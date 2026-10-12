package com.cogitosum.service;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.Payment;
import com.cogitosum.entity.PaymentStatus;
import com.cogitosum.repository.CustomerRepository;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CustomerStatementService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CurrentCompanyContext companyContext;

    public CustomerStatementService(CustomerRepository customerRepository,
                                    InvoiceRepository invoiceRepository,
                                    PaymentRepository paymentRepository,
                                    CurrentCompanyContext companyContext) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.companyContext = companyContext;
    }

    @Transactional(readOnly = true)
    public Statement getStatement(Long customerId, LocalDate startDate, LocalDate endDate) {
        if (customerId == null) {
            throw new IllegalArgumentException("Select a customer");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("Enter a statement end date");
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("The start date must be on or before the end date");
        }

        Long companyId = companyContext.requireCompanyId();
        Customer customer = customerRepository.findByIdAndCompanyId(customerId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        List<StatementEvent> events = new ArrayList<>();

        for (Invoice invoice : invoiceRepository
                .findByCompanyIdAndCustomerIdOrderByInvoiceNumberDesc(companyId, customerId)) {
            if (invoice.getInvoiceDate() == null || invoice.getInvoiceDate().isAfter(endDate)
                    || isExcluded(invoice.getStatus())) {
                continue;
            }
            events.add(new StatementEvent(invoice.getInvoiceDate(), 0, invoice.getInvoiceNumber(),
                    "Invoice " + invoice.getInvoiceNumber(), amount(invoice.getTotalAmount()),
                    BigDecimal.ZERO, "Invoice"));
        }

        for (Payment payment : paymentRepository.findStatementPayments(companyId, customerId)) {
            if (payment.getPaymentDate() == null || payment.getPaymentDate().isAfter(endDate)) {
                continue;
            }
            BigDecimal amount = amount(payment.getAmount());
            if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.COMPLETED) {
                events.add(new StatementEvent(payment.getPaymentDate(), 1, payment.getTransactionId(),
                        "Payment " + payment.getTransactionId(), BigDecimal.ZERO, amount,
                        payment.getStatus() == PaymentStatus.PENDING ? "Pending" : "Payment"));
            } else if (payment.getStatus() == PaymentStatus.REFUNDED) {
                events.add(new StatementEvent(payment.getPaymentDate(), 1, payment.getTransactionId(),
                        "Payment " + payment.getTransactionId(), BigDecimal.ZERO, amount, "Payment"));
                LocalDate refundDate = payment.getUpdatedAt() == null
                        ? payment.getPaymentDate() : payment.getUpdatedAt().toLocalDate();
                if (!refundDate.isAfter(endDate)) {
                    events.add(new StatementEvent(refundDate, 2, payment.getTransactionId(),
                            "Reversal " + payment.getTransactionId(), amount, BigDecimal.ZERO, "Reversal"));
                }
            }
        }

        events.sort(Comparator.comparing(StatementEvent::date)
                .thenComparingInt(StatementEvent::order)
                .thenComparing(StatementEvent::sortKey));

        BigDecimal openingBalance = BigDecimal.ZERO;
        for (StatementEvent event : events) {
            if (startDate != null && event.date().isBefore(startDate)) {
                openingBalance = openingBalance.add(event.debit()).subtract(event.credit());
            }
        }

        BigDecimal runningBalance = openingBalance;
        List<StatementLine> lines = new ArrayList<>();
        for (StatementEvent event : events) {
            if (startDate != null && event.date().isBefore(startDate)) {
                continue;
            }
            runningBalance = runningBalance.add(event.debit()).subtract(event.credit());
            lines.add(new StatementLine(event.date(), event.description(), event.status(),
                    event.debit(), event.credit(), runningBalance));
        }

        return new Statement(customer, startDate, endDate, openingBalance, List.copyOf(lines),
                runningBalance);
    }

    private boolean isExcluded(InvoiceStatus status) {
        return status == null || status == InvoiceStatus.DRAFT
                || status == InvoiceStatus.CANCELLED || status == InvoiceStatus.REFUNDED;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record StatementEvent(LocalDate date, int order, String sortKey, String description,
                                  BigDecimal debit, BigDecimal credit, String status) {
    }

    public record Statement(Customer customer, LocalDate startDate, LocalDate endDate,
                            BigDecimal openingBalance, List<StatementLine> lines,
                            BigDecimal closingBalance) {
    }

    public record StatementLine(LocalDate date, String description, String status,
                                BigDecimal debit, BigDecimal credit, BigDecimal balance) {
    }
}
