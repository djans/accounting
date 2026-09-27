package com.cogitosum.repository;

import com.cogitosum.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Optional<Transfer> findByIdAndCompanyId(Long id, Long companyId);
    List<Transfer> findAllByCompanyId(Long companyId);
}
