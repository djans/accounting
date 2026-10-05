package com.cogitosum.repository;

import com.cogitosum.entity.TaxReturnRowSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxReturnRowSnapshotRepository extends JpaRepository<TaxReturnRowSnapshot, Long> {
    List<TaxReturnRowSnapshot> findByPeriodIdOrderByDisplayOrderAsc(Long periodId);
}
