package com.financialplatform.customer.service;

import com.financialplatform.customer.dto.CustomerResponse;
import com.financialplatform.customer.entity.Customer;
import com.financialplatform.customer.mapper.CustomerMapper;
import com.financialplatform.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<CustomerResponse> getAllCustomers() {

        List<Customer> customers = customerRepository.findAll();

        return customers.stream()
                .map(CustomerMapper::toResponse)
                .toList();
    }

    public Optional<Customer> getCustomerById(Long customerId) {
        return customerRepository.findById(customerId);
    }
}