package com.financialplatform.transaction.controller;

import com.financialplatform.common.response.ApiResponse;
import com.financialplatform.transaction.dto.TransactionRequest;
import com.financialplatform.transaction.dto.TransactionResponse;
import com.financialplatform.transaction.entity.Transaction;
import com.financialplatform.transaction.entity.TransactionStatus;
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
        description = "Transaction submission, execution, and retrieval"
)
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
            summary = "Submit and process transaction",
            description = """
                    Submits a transaction using an idempotency key.

                    DEPOSIT and WITHDRAWAL transactions are processed
                    immediately through Account Service.

                    TRANSFER transactions remain PENDING until the safe
                    transfer-compensation workflow is available.

                    Reusing the same Idempotency-Key with the same request
                    returns the existing transaction without moving money twice.
                    """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>>
    submitTransaction(

            @Parameter(
                    description = "Unique key that prevents duplicate transaction processing",
                    required = true,
                    example = "deposit-account-21-001"
            )
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            )
            String idempotencyKey,

            @Valid
            @RequestBody
            TransactionRequest request) {

        Transaction transaction =
                transactionService.submitTransaction(
                        idempotencyKey,
                        request
                );

        TransactionResponse transactionResponse =
                TransactionResponse.from(transaction);

        String message =
                getSubmissionMessage(
                        transaction.getTransactionStatus()
                );

        HttpStatus responseStatus =
                getSubmissionStatus(
                        transaction.getTransactionStatus()
                );

        return ResponseEntity
                .status(responseStatus)
                .body(
                        new ApiResponse<>(
                                true,
                                message,
                                transactionResponse
                        )
                );
    }

    @Operation(
            summary = "Get transaction by reference",
            description = "Retrieves a transaction and its current processing status"
    )
    @GetMapping("/reference/{transactionReference}")
    public ResponseEntity<ApiResponse<TransactionResponse>>
    getTransactionByReference(

            @PathVariable
            String transactionReference) {

        Transaction transaction =
                transactionService
                        .getTransactionByReference(
                                transactionReference
                        );

        TransactionResponse transactionResponse =
                TransactionResponse.from(transaction);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Transaction retrieved successfully",
                        transactionResponse
                )
        );
    }

    private String getSubmissionMessage(
            TransactionStatus status) {

        return switch (status) {

            case COMPLETED ->
                    "Transaction completed successfully";

            case PENDING ->
                    "Transaction recorded; processing is pending";

            case FAILED ->
                    "Transaction processing previously failed";
        };
    }

    private HttpStatus getSubmissionStatus(
            TransactionStatus status) {

        return switch (status) {

            case COMPLETED,
                 FAILED ->
                    HttpStatus.OK;

            case PENDING ->
                    HttpStatus.ACCEPTED;
        };
    }
}