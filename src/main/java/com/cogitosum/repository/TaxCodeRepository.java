package com.cogitosum.repository;

import com.cogitosum.entity.TaxCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxCodeRepository extends JpaRepository<TaxCode, Long> {
    List<TaxCode> findAllByOrderByCodeAsc();
    Optional<TaxCode> findByCode(String code);
    List<TaxCode> findByAgencyIdOrderByCodeAsc(Long agencyId);
    List<TaxCode> findByIsActiveOrderByCodeAsc(Boolean isActive);
}
