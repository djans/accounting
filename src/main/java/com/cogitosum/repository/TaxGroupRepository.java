package com.cogitosum.repository;

import com.cogitosum.entity.TaxGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaxGroupRepository extends JpaRepository<TaxGroup, Long> {
    Optional<TaxGroup> findByCode(String code);
    List<TaxGroup> findByIsActiveOrderByCodeAsc(Boolean isActive);
    List<TaxGroup> findAllByOrderByCodeAsc();
}
