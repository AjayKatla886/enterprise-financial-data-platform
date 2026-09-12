package com.financialplatform.customer.service;

import com.financialplatform.customer.dto.CustomerAddressRequest;
import com.financialplatform.customer.dto.CustomerAddressResponse;
import com.financialplatform.customer.entity.Customer;
import com.financialplatform.customer.entity.CustomerAddress;
import com.financialplatform.customer.exception.CustomerBusinessException;
import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.customer.mapper.CustomerAddressMapper;
import com.financialplatform.customer.repository.CustomerAddressRepository;
import com.financialplatform.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.financialplatform.customer.entity.CustomerStatus;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerAddressService {

    private final CustomerAddressRepository customerAddressRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public CustomerAddressResponse addAddress(
            Long customerId,
            CustomerAddressRequest request) {

        log.info(
                "Add customer address requested. customerId={}, addressType={}",
                customerId,
                request.addressType()
        );

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerBusinessException(
                        ErrorCode.CUSTOMER_NOT_FOUND,
                        "Customer not found with ID: " + customerId
                ));

        if (customer.getCustomerStatus() != CustomerStatus.ACTIVE) {

            log.warn(
                    "Address creation rejected for inactive customer. customerId={}",
                    customerId
            );

            throw new CustomerBusinessException(
                    ErrorCode.CUSTOMER_INACTIVE,
                    "Address cannot be added for an inactive customer"
            );
        }

        customerAddressRepository
                .findByCustomerIdAndAddressTypeAndCurrentTrue(
                        customerId,
                        request.addressType()
                )
                .ifPresent(existingAddress -> {

                    if (isSameAddress(existingAddress, request)) {

                        log.warn(
                                "Duplicate current address rejected. customerId={}, addressType={}",
                                customerId,
                                request.addressType()
                        );

                        throw new CustomerBusinessException(
                                ErrorCode.DUPLICATE_ADDRESS,
                                "The same current address already exists for this address type"
                        );
                    }

                    LocalDateTime now = LocalDateTime.now();

                    existingAddress.setCurrent(false);
                    existingAddress.setValidTo(now);
                    existingAddress.setUpdatedAt(now);

                    customerAddressRepository.save(existingAddress);

                    log.info(
                            "Previous current address moved to history. customerId={}, addressType={}, addressId={}",
                            customerId,
                            request.addressType(),
                            existingAddress.getAddressId()
                    );
                });

        LocalDateTime now = LocalDateTime.now();

        CustomerAddress newAddress =
                CustomerAddress.builder()
                        .customerId(customer.getCustomerId())
                        .addressType(request.addressType())
                        .addressLine1(request.addressLine1())
                        .addressLine2(request.addressLine2())
                        .city(request.city())
                        .state(request.state())
                        .postalCode(request.postalCode())
                        .country(request.country())
                        .current(true)
                        .validFrom(now)
                        .validTo(null)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        CustomerAddress savedAddress =
                customerAddressRepository.save(newAddress);

        log.info(
                "Customer address created successfully. customerId={}, addressId={}, addressType={}",
                customerId,
                savedAddress.getAddressId(),
                savedAddress.getAddressType()
        );

        return CustomerAddressMapper.toResponse(savedAddress);
    }

    public List<CustomerAddressResponse> getAddressHistory(
            Long customerId) {

        validateCustomerExists(customerId);

        log.debug(
                "Fetching customer address history. customerId={}",
                customerId
        );

        return customerAddressRepository
                .findByCustomerIdOrderByValidFromDesc(customerId)
                .stream()
                .map(CustomerAddressMapper::toResponse)
                .toList();
    }

    public List<CustomerAddressResponse> getCurrentAddresses(
            Long customerId) {

        validateCustomerExists(customerId);

        log.debug(
                "Fetching current customer addresses. customerId={}",
                customerId
        );

        return customerAddressRepository
                .findByCustomerIdAndCurrentTrue(customerId)
                .stream()
                .map(CustomerAddressMapper::toResponse)
                .toList();
    }

    public CustomerAddressResponse getAddressById(
            Long customerId,
            Long addressId) {

        validateCustomerExists(customerId);

        CustomerAddress address =
                customerAddressRepository.findById(addressId)
                        .orElseThrow(() -> new CustomerBusinessException(
                                ErrorCode.ADDRESS_NOT_FOUND,
                                "Address not found with ID: " + addressId
                        ));

        if (!address.getCustomerId().equals(customerId)) {
            throw new CustomerBusinessException(
                    ErrorCode.ADDRESS_NOT_FOUND,
                    "Address not found with ID: " + addressId
            );
        }
        return CustomerAddressMapper.toResponse(address);
    }

    private boolean isSameAddress(
            CustomerAddress existingAddress,
            CustomerAddressRequest request) {

        return normalize(existingAddress.getAddressLine1())
                .equals(normalize(request.addressLine1()))
                &&
                normalize(existingAddress.getAddressLine2())
                        .equals(normalize(request.addressLine2()))
                &&
                normalize(existingAddress.getCity())
                        .equals(normalize(request.city()))
                &&
                normalize(existingAddress.getState())
                        .equals(normalize(request.state()))
                &&
                normalize(existingAddress.getPostalCode())
                        .equals(normalize(request.postalCode()))
                &&
                normalize(existingAddress.getCountry())
                        .equals(normalize(request.country()));
    }

    private String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value.trim().toUpperCase();
    }

    private void validateCustomerExists(Long customerId) {

        if (!customerRepository.existsById(customerId)) {
            throw new CustomerBusinessException(
                    ErrorCode.CUSTOMER_NOT_FOUND,
                    "Customer not found with ID: " + customerId
            );
        }
    }
}