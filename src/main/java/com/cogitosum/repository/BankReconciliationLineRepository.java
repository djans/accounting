package com.cogitosum.repository;

import com.cogitosum.entity.BankReconciliationLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BankReconciliationLineRepository extends JpaRepository<BankReconciliationLine, Long> {
    List<BankReconciliationLine> findBySessionIdOrderByTransactionDateAscIdAsc(Long sessionId);

    List<BankReconciliationLine> findByJournalEntryIdIn(Collection<Long> journalEntryIds);
}
