package com.cogitosum.repository;

import com.cogitosum.entity.InvoiceTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceTemplateRepository extends JpaRepository<InvoiceTemplate, String> {

    @Query("""
        select template.id as id, template.name as name, template.languageTag as languageTag,
               template.originalFilename as originalFilename
        from InvoiceTemplate template
        where template.company.id = :companyId
        order by template.languageTag, template.name
        """)
    List<Metadata> findMetadataByCompanyId(@Param("companyId") Long companyId);

    Optional<InvoiceTemplate> findByIdAndCompanyId(String id, Long companyId);

    boolean existsByCompanyIdAndLanguageTagAndNameIgnoreCase(
        Long companyId, String languageTag, String name);

    interface Metadata {
        String getId();
        String getName();
        String getLanguageTag();
        String getOriginalFilename();
    }
}
