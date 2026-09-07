package com.financialplatform.customer.service;

import com.financialplatform.common.response.PageResponse;
import com.financialplatform.customer.dto.CustomerRequest;
import com.financialplatform.customer.dto.CustomerResponse;
import com.financialplatform.customer.entity.Customer;
import com.financialplatform.customer.mapper.CustomerMapper;
import com.financialplatform.customer.repository.CustomerRepository;
import com.financialplatform.customer.specification.CustomerSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import com.financialplatform.customer.exception.CustomerNotFoundException;
import com.financialplatform.customer.dto.CustomerPatchRequest;
import org.springframework.transaction.annotation.Transactional;
import com.financialplatform.customer.entity.CustomerStatus;
import com.financialplatform.customer.exception.DuplicateCustomerException;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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

    public CustomerService(CustomerRepository customerRepository, EntityManager entityManager) {
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

        if (!sortDir.equalsIgnoreCase("asc")
                && !sortDir.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException(
                    "Sort direction must be either 'asc' or 'desc'"
            );
        }
        Sort.Direction direction =
                sortDir.equalsIgnoreCase("desc")
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException(
                    "Invalid sort field: " + sortBy
            );
        }

        String validatedSortBy = sortBy;

        Sort sort =
                Sort.by(direction, validatedSortBy);

        Pageable pageable =
                PageRequest.of(page, size, sort);

        Page<Customer> customerPage;

        boolean hasFilters =
                (status != null && !status.isBlank())
                        || (firstName != null && !firstName.isBlank())
                        || (lastName != null && !lastName.isBlank())
                        || (customerNumber != null && !customerNumber.isBlank());

        if (hasFilters) {

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

            customerPage =
                    customerRepository.searchCustomers(
                            search.trim(),
                            pageable
                    );

        } else {

            customerPage =
                    customerRepository.findAll(pageable);
        }

        List<CustomerResponse> customers =
                customerPage.getContent()
                        .stream()
                        .map(CustomerMapper::toResponse)
                        .toList();

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

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        return CustomerMapper.toResponse(customer);
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {

        if (customerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateCustomerException(
                    "Customer with this email already exists"
            );
        }
        Customer customer = new Customer();
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setEmail(request.email().trim().toLowerCase());
        customer.setPhoneNumber(request.phoneNumber());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setCustomerStatus(CustomerStatus.ACTIVE);

        LocalDateTime now = LocalDateTime.now();

        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);

        Customer savedCustomer = customerRepository.saveAndFlush(customer);

        entityManager.refresh(savedCustomer);

        return CustomerMapper.toResponse(savedCustomer);
    }

    @Transactional
    public CustomerResponse updateCustomer(
            Long customerId,
            CustomerRequest request) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        // Prevent changing the customer's email to an email
        // already owned by another customer.
        if (!customer.getEmail().equalsIgnoreCase(request.email())
                && customerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateCustomerException(
                    "Customer with this email already exists"
            );
        }

        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setEmail(request.email().trim().toLowerCase());
        customer.setPhoneNumber(request.phoneNumber());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setUpdatedAt(LocalDateTime.now());

        Customer updatedCustomer =
                customerRepository.save(customer);

        return CustomerMapper.toResponse(updatedCustomer);
    }

    @Transactional
    public CustomerResponse patchCustomer(
            Long customerId,
            CustomerPatchRequest request) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        if (request.firstName() != null) {
            customer.setFirstName(request.firstName().trim());
        }

        if (request.lastName() != null) {
            customer.setLastName(request.lastName().trim());
        }

        if (request.email() != null) {

            String normalizedEmail =
                    request.email().trim().toLowerCase();

            if (!customer.getEmail().equalsIgnoreCase(normalizedEmail)
                    && customerRepository.existsByEmailIgnoreCase(normalizedEmail)) {

                throw new DuplicateCustomerException(
                        "Customer with this email already exists"
                );
            }

            customer.setEmail(normalizedEmail);
        }

        if (request.phoneNumber() != null) {
            customer.setPhoneNumber(request.phoneNumber());
        }

        if (request.dateOfBirth() != null) {
            customer.setDateOfBirth(request.dateOfBirth());
        }

        customer.setUpdatedAt(LocalDateTime.now());

        Customer updatedCustomer =
                customerRepository.save(customer);

        return CustomerMapper.toResponse(updatedCustomer);
    }
    @Transactional
    public CustomerResponse deactivateCustomer(Long customerId) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        if (customer.getCustomerStatus() == CustomerStatus.INACTIVE) {
            throw new DuplicateCustomerException(
                    "Customer is already inactive"
            );
        }

        customer.setCustomerStatus(CustomerStatus.INACTIVE);
        customer.setUpdatedAt(LocalDateTime.now());

        Customer updatedCustomer =
                customerRepository.save(customer);

        return CustomerMapper.toResponse(updatedCustomer);
    }

}