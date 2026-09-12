package com.financialplatform.customer.controller;

import com.financialplatform.common.response.ApiResponse;
import com.financialplatform.customer.dto.CustomerKycRequest;
import com.financialplatform.customer.dto.CustomerKycResponse;
import com.financialplatform.customer.dto.CustomerKycStatusRequest;
import com.financialplatform.customer.service.CustomerKycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Customer KYC Management",
        description = "APIs for customer identity verification and KYC lifecycle management"
)
@RestController
@RequestMapping("/api/v1/customers/{customerId}/kyc")
@RequiredArgsConstructor
public class CustomerKycController {

    private final CustomerKycService customerKycService;

    @Operation(
            summary = "Create customer KYC",
            description = "Creates a KYC record for an active customer with initial PENDING status"
    )
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerKycResponse>> createKyc(
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerKycRequest request) {

        CustomerKycResponse response =
                customerKycService.createKyc(customerId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(
                        true,
                        "Customer KYC created successfully",
                        response
                ));
    }

    @Operation(
            summary = "Get customer KYC",
            description = "Retrieves the KYC information associated with a customer"
    )
    @GetMapping
    public ResponseEntity<ApiResponse<CustomerKycResponse>> getKyc(
            @PathVariable Long customerId) {

        CustomerKycResponse response =
                customerKycService.getKycByCustomerId(customerId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Customer KYC retrieved successfully",
                        response
                )
        );
    }

    @Operation(
            summary = "Update customer KYC status",
            description = "Updates the KYC verification status and review timestamps for a customer"
    )
    @PatchMapping("/status")
    public ResponseEntity<ApiResponse<CustomerKycResponse>> updateKycStatus(
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerKycStatusRequest request) {

        CustomerKycResponse response =
                customerKycService.updateKycStatus(customerId, request);

        String message = getKycStatusMessage(response.kycStatus());

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        message,
                        response
                )
        );
    }
    private String getKycStatusMessage(String status) {

        return switch (status) {

            case "PENDING" ->
                    "KYC submitted successfully and is awaiting verification.";

            case "VERIFIED" ->
                    "KYC verified successfully.";

            case "REVIEW_REQUIRED" ->
                    "KYC requires additional review.";

            case "REJECTED" ->
                    "KYC verification was rejected.";

            default ->
                    "KYC status updated successfully.";
        };
    }

}