package com.cogitosum.repository;

import com.cogitosum.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {
    List<Vendor> findAllByOrderByBusinessNameAsc();
    Optional<Vendor> findByEmail(String email);
    Optional<Vendor> findByBusinessNameIgnoreCase(String businessName);
}
