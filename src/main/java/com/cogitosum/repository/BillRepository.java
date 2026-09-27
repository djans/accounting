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
    List<Bill> findAllByOrderByBillNumberDesc();
    Optional<Bill> findByBillNumber(String billNumber);
    List<Bill> findByVendorIdOrderByBillNumberDesc(Long vendorId);
    List<Bill> findByStatusOrderByBillNumberDesc(BillStatus status);
    List<Bill> findByBillDateBetweenOrderByBillDateDesc(LocalDate startDate, LocalDate endDate);
    List<Bill> findByVendorIdAndStatusOrderByBillNumberDesc(Long vendorId, BillStatus status);
    Long countByVendorProvince(String province);
    Optional<Bill> findByIdAndCompanyId(Long id, Long companyId);
    List<Bill> findAllByCompanyIdOrderByBillNumberDesc(Long companyId);
    Optional<Bill> findByCompanyIdAndBillNumber(Long companyId, String billNumber);
    List<Bill> findByCompanyIdAndVendorIdOrderByBillNumberDesc(Long companyId, Long vendorId);
    List<Bill> findByCompanyIdAndStatusOrderByBillNumberDesc(Long companyId, BillStatus status);
    List<Bill> findByCompanyIdAndBillDateBetweenOrderByBillDateDesc(Long companyId, LocalDate startDate, LocalDate endDate);
    Long countByCompanyIdAndVendorProvince(Long companyId, String province);
}
