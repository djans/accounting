package com.cogitosum.service;

import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.entity.Payment;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.GeneralJournalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentPostingServiceTest {

    @Mock private GeneralJournalService journalService;
    @Mock private GeneralJournalRepository journals;
    @Mock private ChartOfAccountRepository accounts;
    @Mock private CurrentCompanyContext companyContext;

    private PaymentPostingService service;

    @BeforeEach
    void setUp() {
        service = new PaymentPostingService();
        ReflectionTestUtils.setField(service, "journalService", journalService);
        ReflectionTestUtils.setField(service, "journalRepository", journals);
        ReflectionTestUtils.setField(service, "accountRepository", accounts);
        ReflectionTestUtils.setField(service, "companyContext", companyContext);
        when(companyContext.requireCompanyId()).thenReturn(2L);
    }

    @Test
    void doesNotCreateASecondReversalWhenPaymentJournalWasAlreadyReversed() {
        GeneralJournal original = journal("PAYMENT-7", JournalStatus.REVERSED);
        GeneralJournal priorReversal = journal("Reversal of JL-7", JournalStatus.POSTED);
        when(journals.findAllByCompanyId(2L)).thenReturn(List.of(original, priorReversal));
        Payment payment = new Payment();
        payment.setId(7L);

        service.reversePayment(payment, "Payment journal was already reversed");

        verifyNoInteractions(journalService);
    }

    private GeneralJournal journal(String reference, JournalStatus status) {
        GeneralJournal journal = new GeneralJournal();
        journal.setReference(reference);
        journal.setStatus(status);
        return journal;
    }
}
