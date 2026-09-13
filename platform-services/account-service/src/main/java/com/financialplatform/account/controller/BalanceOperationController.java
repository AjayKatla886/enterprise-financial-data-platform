package com.financialplatform.account.controller;

import com.financialplatform.account.dto.BalanceOperationRequest;
import com.financialplatform.account.dto.BalanceOperationResponse;
import com.financialplatform.account.entity.BalanceOperation;
import com.financialplatform.account.service.BalanceOperationService;
import com.financialplatform.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/internal/api/v1/accounts")
@RequiredArgsConstructor
@Tag(
        name = "Internal Account Balance Operations",
        description = "Internal APIs for atomic account debit and credit operations"
)
public class BalanceOperationController {

    private final BalanceOperationService balanceOperationService;

    @Operation(
            summary = "Apply account balance operation",
            description = """
                    Atomically applies a DEBIT or CREDIT operation to an active
                    account. The Operation-Reference header provides idempotency
                    and prevents the balance from being updated twice.
                    """
    )
    @PostMapping("/{accountId}/balance-operations")
    public ResponseEntity<ApiResponse<BalanceOperationResponse>>
    applyBalanceOperation(

            @PathVariable
            @Positive(message = "Account ID must be greater than zero")
            Long accountId,

            @Parameter(
                    description = "Unique idempotency reference for this balance operation",
                    required = true,
                    example = "txn-12345-debit"
            )
            @RequestHeader(
                    value = "Operation-Reference",
                    required = false
            )
            String operationReference,

            @Valid
            @RequestBody
            BalanceOperationRequest request) {

        BalanceOperation operation =
                balanceOperationService.applyBalanceOperation(
                        accountId,
                        operationReference,
                        request
                );

        BalanceOperationResponse operationResponse =
                BalanceOperationResponse.from(operation);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Balance operation completed successfully",
                        operationResponse
                )
        );
    }

    @Operation(
            summary = "Get balance operation",
            description = "Retrieves a balance operation using its unique operation reference"
    )
    @GetMapping("/balance-operations/{operationReference}")
    public ResponseEntity<ApiResponse<BalanceOperationResponse>>
    getBalanceOperation(

            @PathVariable
            String operationReference) {

        BalanceOperation operation =
                balanceOperationService
                        .getByOperationReference(operationReference);

        BalanceOperationResponse operationResponse =
                BalanceOperationResponse.from(operation);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Balance operation retrieved successfully",
                        operationResponse
                )
        );
    }
}