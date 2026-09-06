package com.financialplatform.customer.controller;

import com.financialplatform.common.constants.ApplicationConstants;
import com.financialplatform.common.response.ApiResponse;
import com.financialplatform.customer.service.CustomerService;
import com.financialplatform.customer.dto.CustomerResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getAllCustomers() {

        List<CustomerResponse> customers = customerService.getAllCustomers();

        ApiResponse<List<CustomerResponse>> response =
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
}