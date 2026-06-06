package com.cogitosum.repository;

import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

