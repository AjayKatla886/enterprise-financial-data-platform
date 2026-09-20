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
        description = "Transaction submission, execution, recovery, and retrieval"
)
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
            summary = "Submit and process transaction",
            description = """
                    Submits and executes a transaction using an
                    idempotency key.

                    DEPOSIT operations credit the target account.

                    WITHDRAWAL operations debit the source account.

                    TRANSFER operations atomically debit the source
                    account and credit the target account through
                    Account Service.

                    Reusing the same Idempotency-Key with the same
                    request returns the existing transaction without
                    moving money twice.

                    If Account Service does not return a conclusive
                    response, the transaction is marked as
                    RECONCILIATION_REQUIRED instead of FAILED.
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

        TransactionStatus transactionStatus =
                transaction.getTransactionStatus();

        String message =
                getSubmissionMessage(transactionStatus);

        HttpStatus responseStatus =
                getSubmissionStatus(transactionStatus);

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
            description = """
                    Retrieves a transaction using its unique reference
                    and returns its current processing status.
                    """
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

            case PENDING ->
                    "Transaction recorded; processing is pending";

            case PROCESSING ->
                    "Transaction is currently being processed";

            case COMPLETED ->
                    "Transaction completed successfully";

            case FAILED ->
                    "Transaction processing previously failed";

            case RECONCILIATION_REQUIRED ->
                    "Transaction outcome could not be confirmed; reconciliation is required";
        };
    }

    private HttpStatus getSubmissionStatus(
            TransactionStatus status) {

        return switch (status) {

            case COMPLETED,
                 FAILED ->
                    HttpStatus.OK;

            case PENDING,
                 PROCESSING,
                 RECONCILIATION_REQUIRED ->
                    HttpStatus.ACCEPTED;
        };
    }
    @Operation(
            summary = "Reconcile transaction",
            description = """
                Checks the Account Service ledger to determine the outcome
                of a PROCESSING or RECONCILIATION_REQUIRED transaction.

                This endpoint only verifies the existing operation.
                It does not execute the debit, credit, or transfer again.
                """
    )
    @PostMapping("/{transactionReference}/reconcile")
    public ResponseEntity<ApiResponse<TransactionResponse>>
    reconcileTransaction(
            @PathVariable String transactionReference) {

        Transaction transaction =
                transactionService.reconcileTransaction(
                        transactionReference
                );

        TransactionResponse response =
                TransactionResponse.from(transaction);

        String message =
                transaction.getTransactionStatus()
                        == TransactionStatus.COMPLETED
                        ? "Transaction reconciliation completed successfully"
                        : "Transaction still requires reconciliation";

        HttpStatus status =
                transaction.getTransactionStatus()
                        == TransactionStatus.COMPLETED
                        ? HttpStatus.OK
                        : HttpStatus.ACCEPTED;

        return ResponseEntity
                .status(status)
                .body(
                        new ApiResponse<>(
                                true,
                                message,
                                response
                        )
                );
    }
}