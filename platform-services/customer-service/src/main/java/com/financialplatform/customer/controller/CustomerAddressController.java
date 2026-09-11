package com.financialplatform.customer.controller;

import com.financialplatform.common.response.ApiResponse;
import com.financialplatform.customer.dto.CustomerAddressRequest;
import com.financialplatform.customer.dto.CustomerAddressResponse;
import com.financialplatform.customer.service.CustomerAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/customers/{customerId}/addresses")
@RequiredArgsConstructor
@Tag(
        name = "Customer Address Management",
        description = "APIs for customer address creation, current-address retrieval, and address history"
)
public class CustomerAddressController {

    private final CustomerAddressService customerAddressService;

    @Operation(
            summary = "Add customer address",
            description = "Adds a new address for an active customer and maintains address history when an existing current address is replaced"
    )
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerAddressResponse>> addAddress(
            @PathVariable
            @Positive(message = "Customer ID must be greater than zero")
            Long customerId,

            @Valid
            @RequestBody
            CustomerAddressRequest request) {

        CustomerAddressResponse address =
                customerAddressService.addAddress(customerId, request);

        ApiResponse<CustomerAddressResponse> response =
                new ApiResponse<>(
                        true,
                        "Customer address added successfully",
                        address
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(
            summary = "Get customer address history",
            description = "Retrieves all current and historical addresses for a customer ordered by address validity history"
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerAddressResponse>>>
    getAddressHistory(
            @PathVariable
            @Positive(message = "Customer ID must be greater than zero")
            Long customerId) {

        List<CustomerAddressResponse> addresses =
                customerAddressService.getAddressHistory(customerId);

        ApiResponse<List<CustomerAddressResponse>> response =
                new ApiResponse<>(
                        true,
                        "Customer address history retrieved successfully",
                        addresses
                );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get current customer addresses",
            description = "Retrieves only the addresses currently active for a customer"
    )
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<List<CustomerAddressResponse>>>
    getCurrentAddresses(
            @PathVariable
            @Positive(message = "Customer ID must be greater than zero")
            Long customerId) {

        List<CustomerAddressResponse> addresses =
                customerAddressService.getCurrentAddresses(customerId);

        ApiResponse<List<CustomerAddressResponse>> response =
                new ApiResponse<>(
                        true,
                        "Current customer addresses retrieved successfully",
                        addresses
                );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get customer address by ID",
            description = "Retrieves a specific address and verifies that it belongs to the requested customer"
    )
    @GetMapping("/{addressId}")
    public ResponseEntity<ApiResponse<CustomerAddressResponse>>
    getAddressById(
            @PathVariable
            @Positive(message = "Customer ID must be greater than zero")
            Long customerId,

            @PathVariable
            @Positive(message = "Address ID must be greater than zero")
            Long addressId) {

        CustomerAddressResponse address =
                customerAddressService.getAddressById(
                        customerId,
                        addressId
                );

        ApiResponse<CustomerAddressResponse> response =
                new ApiResponse<>(
                        true,
                        "Customer address retrieved successfully",
                        address
                );

        return ResponseEntity.ok(response);
    }
}