package com.financialplatform.transaction.controller;

import com.financialplatform.common.response.ApiResponse;
import com.financialplatform.transaction.dto.TransactionRequest;
import com.financialplatform.transaction.dto.TransactionResponse;
import com.financialplatform.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(
        name = "Transaction Management",
        description = "Transaction request submission and retrieval"
)
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
            summary = "Submit transaction request",
            description = "Validates referenced accounts and records a PENDING "
                    + "transaction. Reusing the same Idempotency-Key with the "
                    + "same request returns the existing transaction."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> submitTransaction(
            @Parameter(
                    description = "Unique key that prevents duplicate transaction creation",
                    required = true,
                    example = "deposit-account-21-001"
            )
            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @Valid @RequestBody TransactionRequest request) {

        TransactionResponse transactionResponse =
                TransactionResponse.from(
                        transactionService.submitTransaction(
                                idempotencyKey,
                                request
                        )
                );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(
                        true,
                        "Transaction request recorded; processing is pending",
                        transactionResponse
                ));
    }

    @Operation(
            summary = "Get transaction by reference",
            description = "Retrieves a recorded transaction and its current status"
    )
    @GetMapping("/reference/{transactionReference}")
    public ResponseEntity<ApiResponse<TransactionResponse>>
    getTransactionByReference(
            @PathVariable String transactionReference) {

        TransactionResponse transactionResponse =
                TransactionResponse.from(
                        transactionService.getTransactionByReference(
                                transactionReference
                        )
                );

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Transaction retrieved successfully",
                        transactionResponse
                )
        );
    }
}