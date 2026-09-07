package com.financialplatform.customer.controller;

import com.financialplatform.common.constants.ApplicationConstants;
import com.financialplatform.common.response.ApiResponse;
import com.financialplatform.common.response.PageResponse;
import com.financialplatform.customer.dto.CustomerResponse;
import com.financialplatform.customer.service.CustomerService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.financialplatform.customer.dto.CustomerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import com.financialplatform.customer.dto.CustomerPatchRequest;
import org.springframework.web.bind.annotation.PatchMapping;


import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
@Validated
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> getAllCustomers(

            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String customerNumber,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number cannot be negative")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size cannot exceed 100")
            int size,

            @RequestParam(defaultValue = "createdAt")
            String sortBy,

            @RequestParam(defaultValue = "desc")
            String sortDir) {

        PageResponse<CustomerResponse> customers =
                customerService.getAllCustomers(
                        search,
                        status,
                        firstName,
                        lastName,
                        customerNumber,
                        page,
                        size,
                        sortBy,
                        sortDir
                );

        ApiResponse<PageResponse<CustomerResponse>> response =
                new ApiResponse<>(
                        true,
                        "Customers fetched successfully",
                        customers
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {

        Map<String, String> serviceDetails = Map.of(
                "service", ApplicationConstants.CUSTOMER_SERVICE,
                "status", ApplicationConstants.STATUS_UP
        );

        ApiResponse<Map<String, String>> response =
                new ApiResponse<>(
                        true,
                        "Customer Service is running",
                        serviceDetails
                );

        return ResponseEntity.ok(response);
    }
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CustomerRequest request) {

        CustomerResponse customer =
                customerService.createCustomer(request);

        ApiResponse<CustomerResponse> response =
                new ApiResponse<>(
                        true,
                        "Customer created successfully",
                        customer
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @GetMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerById(
            @PathVariable Long customerId) {

        CustomerResponse customer =
                customerService.getCustomerById(customerId);

        ApiResponse<CustomerResponse> response =
                new ApiResponse<>(
                        true,
                        "Customer fetched successfully",
                        customer
                );

        return ResponseEntity.ok(response);
    }
    @PutMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerRequest request) {

        CustomerResponse customer =
                customerService.updateCustomer(customerId, request);

        ApiResponse<CustomerResponse> response =
                new ApiResponse<>(
                        true,
                        "Customer updated successfully",
                        customer
                );

        return ResponseEntity.ok(response);
    }
    @PatchMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerResponse>> patchCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerPatchRequest request) {

        CustomerResponse customer =
                customerService.patchCustomer(customerId, request);

        ApiResponse<CustomerResponse> response =
                new ApiResponse<>(
                        true,
                        "Customer updated successfully",
                        customer
                );

        return ResponseEntity.ok(response);
    }
    @PatchMapping("/{customerId}/deactivate")
    public ResponseEntity<ApiResponse<CustomerResponse>> deactivateCustomer(
            @PathVariable Long customerId) {

        CustomerResponse customer =
                customerService.deactivateCustomer(customerId);

        ApiResponse<CustomerResponse> response =
                new ApiResponse<>(
                        true,
                        "Customer deactivated successfully",
                        customer
                );

        return ResponseEntity.ok(response);
    }
}