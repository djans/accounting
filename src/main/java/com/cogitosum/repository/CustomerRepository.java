package com.cogitosum.repository;

import com.cogitosum.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByOrderByBusinessNameAsc();
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByGstNumber(String gstNumber);
    Optional<Customer> findByIdAndCompanyId(Long id, Long companyId);
    List<Customer> findAllByCompanyIdOrderByBusinessNameAsc(Long companyId);
    Optional<Customer> findByCompanyIdAndEmail(Long companyId, String email);
    Optional<Customer> findByCompanyIdAndGstNumber(Long companyId, String gstNumber);
}
