package com.cogitosum.repository;

import com.cogitosum.entity.TaxCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxCodeRepository extends JpaRepository<TaxCode, Long> {
    Optional<TaxCode> findByCode(String code);
    List<TaxCode> findByAgencyId(Long agencyId);
    List<TaxCode> findByIsActive(Boolean isActive);
}
