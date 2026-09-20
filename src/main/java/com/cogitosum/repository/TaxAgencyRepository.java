package com.cogitosum.repository;

import com.cogitosum.entity.TaxAgency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxAgencyRepository extends JpaRepository<TaxAgency, Long> {
    List<TaxAgency> findAllByOrderByCodeAsc();
    Optional<TaxAgency> findByCode(String code);
}
