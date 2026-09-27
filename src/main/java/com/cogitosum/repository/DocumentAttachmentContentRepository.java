package com.cogitosum.repository;

import com.cogitosum.entity.DocumentAttachmentContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentAttachmentContentRepository extends JpaRepository<DocumentAttachmentContent, Long> {
}
