package com.financialplatform.customer.repository;

import com.financialplatform.customer.entity.CustomerKyc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerKycRepository extends JpaRepository<CustomerKyc, Long> {

    Optional<CustomerKyc> findByCustomerId(Long customerId);

    boolean existsByCustomerId(Long customerId);
}