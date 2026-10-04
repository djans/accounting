package com.cogitosum.repository;

import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GeneralJournalRepository extends JpaRepository<GeneralJournal, Long> {
    Optional<GeneralJournal> findByJournalNumber(String journalNumber);
    List<GeneralJournal> findByStatus(JournalStatus status);
    List<GeneralJournal> findByJournalDateBetween(LocalDate startDate, LocalDate endDate);
    List<GeneralJournal> findByStatusAndJournalDateBetween(JournalStatus status, LocalDate startDate, LocalDate endDate);
    Optional<GeneralJournal> findByIdAndCompanyId(Long id, Long companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GeneralJournal> findLockedByIdAndCompanyId(Long id, Long companyId);
    Optional<GeneralJournal> findByCompanyIdAndJournalNumber(Long companyId, String journalNumber);
    Optional<GeneralJournal> findByCompanyIdAndReference(Long companyId, String reference);
    List<GeneralJournal> findByCompanyIdAndStatus(Long companyId, JournalStatus status);
    List<GeneralJournal> findByCompanyIdAndJournalDateBetween(Long companyId, LocalDate startDate, LocalDate endDate);
    List<GeneralJournal> findByCompanyIdAndStatusAndJournalDateBetween(
            Long companyId, JournalStatus status, LocalDate startDate, LocalDate endDate);
    List<GeneralJournal> findAllByCompanyId(Long companyId);

    @Query("""
            SELECT journal
            FROM GeneralJournal journal
            WHERE journal.company.id = :companyId
            ORDER BY CASE WHEN journal.status = :postedStatus THEN 0 ELSE 1 END,
                     CASE WHEN journal.status = :postedStatus THEN journal.postedDate ELSE journal.createdAt END DESC,
                     journal.id DESC
            """)
    List<GeneralJournal> findAllByCompanyIdOrderByPostedFirstAndRecent(
            @Param("companyId") Long companyId,
            @Param("postedStatus") JournalStatus postedStatus);
}
