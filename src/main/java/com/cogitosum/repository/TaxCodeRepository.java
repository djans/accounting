package com.cogitosum.repository;

import com.cogitosum.entity.TaxCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxCodeRepository extends JpaRepository<TaxCode, Long> {
    List<TaxCode> findAllByOrderByCodeAsc();
    Optional<TaxCode> findByCode(String code);
    List<TaxCode> findByIsActiveOrderByCodeAsc(Boolean isActive);
    Long countBySalesTaxGroupId(Long groupId);
    Long countByPurchaseTaxGroupId(Long groupId);
    Optional<TaxCode> findByIdAndCompanyId(Long id, Long companyId);
    List<TaxCode> findAllByCompanyIdOrderByCodeAsc(Long companyId);
    Optional<TaxCode> findByCompanyIdAndCode(Long companyId, String code);
    List<TaxCode> findByCompanyIdAndIsActiveOrderByCodeAsc(Long companyId, Boolean isActive);
    Long countByCompanyIdAndSalesTaxGroupId(Long companyId, Long groupId);
    Long countByCompanyIdAndPurchaseTaxGroupId(Long companyId, Long groupId);
    
    @Modifying
    @Query("UPDATE TaxCode tc SET tc.salesTaxGroup = NULL WHERE tc.salesTaxGroup.id = :groupId")
    void nullifySalesTaxGroupReferences(@Param("groupId") Long groupId);

    @Modifying
    @Query("UPDATE TaxCode tc SET tc.purchaseTaxGroup = NULL WHERE tc.purchaseTaxGroup.id = :groupId")
    void nullifyPurchaseTaxGroupReferences(@Param("groupId") Long groupId);
}
