package com.financialplatform.customer.service;

import com.financialplatform.common.response.PageResponse;
import com.financialplatform.customer.dto.CustomerPatchRequest;
import com.financialplatform.customer.dto.CustomerRequest;
import com.financialplatform.customer.dto.CustomerResponse;
import com.financialplatform.customer.entity.Customer;
import com.financialplatform.customer.entity.CustomerStatus;
import com.financialplatform.customer.mapper.CustomerMapper;
import com.financialplatform.customer.repository.CustomerRepository;
import com.financialplatform.customer.specification.CustomerSpecification;
import jakarta.persistence.EntityManager;
import com.financialplatform.customer.exception.CustomerBusinessException;
import com.financialplatform.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "customerId",
            "customerNumber",
            "firstName",
            "lastName",
            "email",
            "customerStatus",
            "createdAt",
            "updatedAt"
    );

    private final CustomerRepository customerRepository;
    private final EntityManager entityManager;

    public CustomerService(
            CustomerRepository customerRepository,
            EntityManager entityManager
    ) {
        this.customerRepository = customerRepository;
        this.entityManager = entityManager;
    }

    public PageResponse<CustomerResponse> getAllCustomers(
            String search,
            String status,
            String firstName,
            String lastName,
            String customerNumber,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {

        log.debug(
                "Fetching customers. page={}, size={}, sortBy={}, sortDir={}, hasSearch={}, status={}",
                page,
                size,
                sortBy,
                sortDir,
                search != null && !search.isBlank(),
                status
        );

        if (!sortDir.equalsIgnoreCase("asc")
                && !sortDir.equalsIgnoreCase("desc")) {

            log.warn(
                    "Invalid customer sort direction requested. sortDir={}",
                    sortDir
            );

            throw new IllegalArgumentException(
                    "Sort direction must be either 'asc' or 'desc'"
            );
        }

        Sort.Direction direction =
                sortDir.equalsIgnoreCase("desc")
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {

            log.warn(
                    "Invalid customer sort field requested. sortBy={}",
                    sortBy
            );

            throw new IllegalArgumentException(
                    "Invalid sort field: " + sortBy
            );
        }

        Sort sort =
                Sort.by(direction, sortBy);

        Pageable pageable =
                PageRequest.of(page, size, sort);

        Page<Customer> customerPage;

        boolean hasFilters =
                (status != null && !status.isBlank())
                        || (firstName != null && !firstName.isBlank())
                        || (lastName != null && !lastName.isBlank())
                        || (customerNumber != null && !customerNumber.isBlank());

        if (hasFilters) {

            log.debug("Executing customer query using structured filters");

            Specification<Customer> specification =
                    Specification
                            .where(CustomerSpecification.hasStatus(status))
                            .and(CustomerSpecification.hasFirstName(firstName))
                            .and(CustomerSpecification.hasLastName(lastName))
                            .and(CustomerSpecification.hasCustomerNumber(customerNumber));

            customerPage =
                    customerRepository.findAll(
                            specification,
                            pageable
                    );

        } else if (search != null && !search.isBlank()) {

            log.debug("Executing customer free-text search");

            customerPage =
                    customerRepository.searchCustomers(
                            search.trim(),
                            pageable
                    );

        } else {

            log.debug("Executing customer query without filters");

            customerPage =
                    customerRepository.findAll(pageable);
        }

        List<CustomerResponse> customers =
                customerPage.getContent()
                        .stream()
                        .map(CustomerMapper::toResponse)
                        .toList();

        log.debug(
                "Customer query completed. returned={}, totalElements={}, totalPages={}",
                customers.size(),
                customerPage.getTotalElements(),
                customerPage.getTotalPages()
        );

        return new PageResponse<>(
                customers,
                customerPage.getNumber(),
                customerPage.getSize(),
                customerPage.getTotalElements(),
                customerPage.getTotalPages(),
                customerPage.isFirst(),
                customerPage.isLast()
        );
    }

    public CustomerResponse getCustomerById(Long customerId) {

        log.debug(
                "Fetching customer. customerId={}",
                customerId
        );

        Customer customer =
                customerRepository.findById(customerId)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Customer not found. customerId={}",
                                    customerId
                            );

                            return new CustomerBusinessException(
                                    ErrorCode.CUSTOMER_NOT_FOUND,
                                    "Customer not found with ID: " + customerId
                            );
                        });

        return CustomerMapper.toResponse(customer);
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {

        log.info("Customer creation requested");

        if (customerRepository.existsByEmailIgnoreCase(request.email())) {

            log.warn(
                    "Customer creation rejected because email already exists"
            );

            throw new CustomerBusinessException(
                    ErrorCode.DUPLICATE_CUSTOMER,
                    "Customer with this email already exists"
            );
        }

        Customer customer = new Customer();

        customer.setFirstName(
                request.firstName().trim()
        );

        customer.setLastName(
                request.lastName().trim()
        );

        customer.setEmail(
                request.email()
                        .trim()
                        .toLowerCase()
        );

        customer.setPhoneNumber(
                request.phoneNumber()
        );

        customer.setDateOfBirth(
                request.dateOfBirth()
        );

        customer.setCustomerStatus(
                CustomerStatus.ACTIVE
        );

        LocalDateTime now =
                LocalDateTime.now();

        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);

        Customer savedCustomer =
                customerRepository.saveAndFlush(customer);

        entityManager.refresh(savedCustomer);

        log.info(
                "Customer created successfully. customerId={}, customerNumber={}",
                savedCustomer.getCustomerId(),
                savedCustomer.getCustomerNumber()
        );

        return CustomerMapper.toResponse(savedCustomer);
    }

    @Transactional
    public CustomerResponse updateCustomer(
            Long customerId,
            CustomerRequest request
    ) {

        log.info(
                "Customer update requested. customerId={}",
                customerId
        );

        Customer customer =
                customerRepository.findById(customerId)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Customer update failed because customer was not found. customerId={}",
                                    customerId
                            );

                            return new CustomerBusinessException(
                                    ErrorCode.CUSTOMER_NOT_FOUND,
                                    "Customer not found with ID: " + customerId
                            );
                        });

        if (!customer.getEmail()
                .equalsIgnoreCase(request.email())
                && customerRepository
                .existsByEmailIgnoreCase(request.email())) {

            log.warn(
                    "Customer update rejected because requested email is already in use. customerId={}",
                    customerId
            );

            throw new CustomerBusinessException(
                    ErrorCode.DUPLICATE_CUSTOMER,
                    "Customer with this email already exists"
            );
        }

        customer.setFirstName(
                request.firstName().trim()
        );

        customer.setLastName(
                request.lastName().trim()
        );

        customer.setEmail(
                request.email()
                        .trim()
                        .toLowerCase()
        );

        customer.setPhoneNumber(
                request.phoneNumber()
        );

        customer.setDateOfBirth(
                request.dateOfBirth()
        );

        customer.setUpdatedAt(
                LocalDateTime.now()
        );

        Customer updatedCustomer =
                customerRepository.save(customer);

        log.info(
                "Customer updated successfully. customerId={}",
                customerId
        );

        return CustomerMapper.toResponse(updatedCustomer);
    }

    @Transactional
    public CustomerResponse patchCustomer(
            Long customerId,
            CustomerPatchRequest request
    ) {

        log.info(
                "Customer partial update requested. customerId={}",
                customerId
        );

        Customer customer =
                customerRepository.findById(customerId)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Customer patch failed because customer was not found. customerId={}",
                                    customerId
                            );

                            return new CustomerBusinessException(
                                    ErrorCode.CUSTOMER_NOT_FOUND,
                                    "Customer not found with ID: " + customerId
                            );
                        });

        if (request.firstName() != null) {

            customer.setFirstName(
                    request.firstName().trim()
            );
        }

        if (request.lastName() != null) {

            customer.setLastName(
                    request.lastName().trim()
            );
        }

        if (request.email() != null) {

            String normalizedEmail =
                    request.email()
                            .trim()
                            .toLowerCase();

            if (!customer.getEmail()
                    .equalsIgnoreCase(normalizedEmail)
                    && customerRepository
                    .existsByEmailIgnoreCase(normalizedEmail)) {

                log.warn(
                        "Customer patch rejected because requested email is already in use. customerId={}",
                        customerId
                );

                throw new CustomerBusinessException(
                        ErrorCode.DUPLICATE_CUSTOMER,
                        "Customer with this email already exists"
                );
            }

            customer.setEmail(normalizedEmail);
        }

        if (request.phoneNumber() != null) {

            customer.setPhoneNumber(
                    request.phoneNumber()
            );
        }

        if (request.dateOfBirth() != null) {

            customer.setDateOfBirth(
                    request.dateOfBirth()
            );
        }

        customer.setUpdatedAt(
                LocalDateTime.now()
        );

        Customer updatedCustomer =
                customerRepository.save(customer);

        log.info(
                "Customer partially updated successfully. customerId={}",
                customerId
        );

        return CustomerMapper.toResponse(updatedCustomer);
    }

    @Transactional
    public CustomerResponse deactivateCustomer(
            Long customerId
    ) {

        log.info(
                "Customer deactivation requested. customerId={}",
                customerId
        );

        Customer customer =
                customerRepository.findById(customerId)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Customer deactivation failed because customer was not found. customerId={}",
                                    customerId
                            );

                            throw new CustomerBusinessException(
                                    ErrorCode.CUSTOMER_INACTIVE,
                                    "Customer is already inactive"
                            );
                        });

        if (customer.getCustomerStatus()
                == CustomerStatus.INACTIVE) {

            log.warn(
                    "Customer deactivation rejected because customer is already inactive. customerId={}",
                    customerId
            );

            throw new CustomerBusinessException(
                    ErrorCode.CUSTOMER_INACTIVE,
                    "Customer is already inactive"
            );
        }

        customer.setCustomerStatus(
                CustomerStatus.INACTIVE
        );

        customer.setUpdatedAt(
                LocalDateTime.now()
        );

        Customer updatedCustomer =
                customerRepository.save(customer);

        log.info(
                "Customer deactivated successfully. customerId={}",
                customerId
        );

        return CustomerMapper.toResponse(updatedCustomer);
    }
}