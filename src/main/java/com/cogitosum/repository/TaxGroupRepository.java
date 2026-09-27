package com.cogitosum.repository;

import com.cogitosum.entity.TaxGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TaxGroupRepository extends JpaRepository<TaxGroup, Long> {
    Optional<TaxGroup> findByCode(String code);
    List<TaxGroup> findByIsActiveOrderByCodeAsc(Boolean isActive);
    List<TaxGroup> findAllByOrderByCodeAsc();
    Optional<TaxGroup> findByIdAndCompanyId(Long id, Long companyId);
    Optional<TaxGroup> findByCompanyIdAndCode(Long companyId, String code);
    List<TaxGroup> findByCompanyIdAndIsActiveOrderByCodeAsc(Long companyId, Boolean isActive);
    List<TaxGroup> findAllByCompanyIdOrderByCodeAsc(Long companyId);

    @Modifying
    @Query(value = "DELETE FROM tax_group_items WHERE tax_item_id = :taxItemId", nativeQuery = true)
    void deleteReferencesToTaxItem(@Param("taxItemId") Long taxItemId);
}
