package com.cogitosum.repository;

import com.cogitosum.entity.Bill;
import com.cogitosum.entity.BillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {
    Optional<Bill> findByBillNumber(String billNumber);
    List<Bill> findByVendorId(Long vendorId);
    List<Bill> findByStatus(BillStatus status);
    List<Bill> findByBillDateBetween(LocalDate startDate, LocalDate endDate);
    List<Bill> findByVendorIdAndStatus(Long vendorId, BillStatus status);
}
