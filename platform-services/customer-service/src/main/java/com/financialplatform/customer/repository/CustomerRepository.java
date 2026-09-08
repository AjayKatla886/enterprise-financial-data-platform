package com.financialplatform.customer.repository;

import com.financialplatform.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    @Query("""
            SELECT c
            FROM Customer c
            WHERE LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(c.customerNumber) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<Customer> searchCustomers(
            @Param("search") String search,
            Pageable pageable
    );
    boolean existsByEmailIgnoreCase(String email);
}