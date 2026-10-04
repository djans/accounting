package com.cogitosum.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ChequeRepositoryTest {

    @Autowired private WrittenChequeRepository cheques;
    @Autowired private GeneralJournalRepository journals;

    @Test
    void supportsCompanyScopedLifecycleLookups() {
        assertTrue(cheques.findLockedByIdAndCompanyId(-1L, -1L).isEmpty());
        assertTrue(journals.findByCompanyIdAndReference(-1L, "CHEQUE--1").isEmpty());
    }
}
