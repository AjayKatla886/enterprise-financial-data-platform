package com.financialplatform.customer.repository;

import com.financialplatform.customer.entity.AddressType;
import com.financialplatform.customer.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository
        extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomerIdOrderByValidFromDesc(
            Long customerId
    );

    List<CustomerAddress> findByCustomerIdAndCurrentTrue(
            Long customerId
    );

    Optional<CustomerAddress>
    findByCustomerIdAndAddressTypeAndCurrentTrue(
            Long customerId,
            AddressType addressType
    );
}