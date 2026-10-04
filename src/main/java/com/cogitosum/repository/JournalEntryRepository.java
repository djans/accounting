package com.cogitosum.repository;

import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.JournalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findByAccountId(Long accountId);
    List<JournalEntry> findByAccountIdAndCleared(Long accountId, boolean cleared);
    List<JournalEntry> findByJournalCompanyIdAndAccountId(Long companyId, Long accountId);
    List<JournalEntry> findByJournalCompanyIdAndAccountIdAndCleared(Long companyId, Long accountId, boolean cleared);
    List<JournalEntry> findByJournalCompanyIdAndAccountIdAndClearedAndJournalStatus(
            Long companyId, Long accountId, boolean cleared, JournalStatus status);

    @Query("""
            select entry
            from JournalEntry entry
            where entry.journal.company.id = :companyId
              and entry.account.id = :accountId
              and entry.journal.status = :status
              and entry.journal.journalDate <= :throughDate
            order by entry.journal.journalDate, entry.journal.id, entry.lineNumber
            """)
    List<JournalEntry> findPostedEntriesForAccountThroughDate(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("status") JournalStatus status,
            @Param("throughDate") LocalDate throughDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<JournalEntry> findByIdInAndJournalCompanyId(List<Long> ids, Long companyId);
}
