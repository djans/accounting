package com.cogitosum.service;

import com.cogitosum.entity.FiscalYear;
import com.cogitosum.entity.FiscalYearStatus;
import com.cogitosum.repository.FiscalYearRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class FiscalYearService {

    @Autowired
    private FiscalYearRepository fiscalYearRepository;

    public FiscalYear createFiscalYear(FiscalYear fiscalYear) {
        return fiscalYearRepository.save(fiscalYear);
    }

    public Optional<FiscalYear> getById(Long id) {
        return fiscalYearRepository.findById(id);
    }

    public List<FiscalYear> getAll() {
        return fiscalYearRepository.findAll();
    }

    /**
     * A date is locked when it falls within a fiscal year that has been CLOSED.
     * Used to reject postings into a closed period.
     */
    public boolean isLocked(LocalDate date) {
        if (date == null) return false;
        return fiscalYearRepository.findByStatus(FiscalYearStatus.CLOSED).stream()
            .anyMatch(fy -> fy.covers(date));
    }

    public void deleteFiscalYear(Long id) {
        fiscalYearRepository.deleteById(id);
    }
}
