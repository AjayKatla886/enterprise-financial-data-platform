package com.financialplatform.transaction.controller;

import com.financialplatform.common.response.ApiResponse;
import com.financialplatform.common.response.PageResponse;
import com.financialplatform.transaction.dto.TransactionRequest;
import com.financialplatform.transaction.dto.TransactionResponse;
import com.financialplatform.transaction.dto.TransactionStatusHistoryResponse;
import com.financialplatform.transaction.entity.Transaction;
import com.financialplatform.transaction.entity.TransactionStatus;
import com.financialplatform.transaction.entity.TransactionStatusHistory;
import com.financialplatform.transaction.entity.TransactionType;
import com.financialplatform.transaction.service.TransactionService;
import com.financialplatform.transaction.service.TransactionStatusHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(
        name = "Transaction Management",
        description = """
                Transaction submission, execution, reconciliation,
                audit history, filtering, and retrieval
                """
)
public class TransactionController {

    private final TransactionService transactionService;

    private final TransactionStatusHistoryService
            transactionStatusHistoryService;

    // ============================================================
    // Search Transactions
    // ============================================================

    @Operation(
            summary = "Search transactions",
            description = """
                    Retrieves transactions using optional transaction
                    type, status, and creation-date filters.

                    Results support pagination and sorting.
                    """
    )
    @GetMapping
    public ResponseEntity<
            ApiResponse<PageResponse<TransactionResponse>>>
    getTransactions(

            @RequestParam(required = false)
            TransactionType transactionType,

            @RequestParam(required = false)
            TransactionStatus status,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime toDate,

            @RequestParam(defaultValue = "0")
            @Min(
                    value = 0,
                    message = "Page number must be zero or greater"
            )
            int page,

            @RequestParam(defaultValue = "20")
            @Min(
                    value = 1,
                    message = "Page size must be at least 1"
            )
            @Max(
                    value = 100,
                    message = "Page size must not exceed 100"
            )
            int size,

            @RequestParam(defaultValue = "createdAt")
            String sortBy,

            @RequestParam(defaultValue = "desc")
            String sortDir) {

        return buildTransactionSearchResponse(
                null,
                null,
                transactionType,
                status,
                fromDate,
                toDate,
                page,
                size,
                sortBy,
                sortDir
        );
    }

    // ============================================================
    // Account Transactions
    // ============================================================

    @Operation(
            summary = "Get transactions by account",
            description = """
                    Retrieves transactions involving the specified account.

                    A transaction matches when the account is either the
                    source account or the target account.

                    Results support filtering, pagination, and sorting.
                    """
    )
    @GetMapping("/account/{accountId}")
    public ResponseEntity<
            ApiResponse<PageResponse<TransactionResponse>>>
    getTransactionsByAccount(

            @PathVariable
            @Positive(
                    message = "Account ID must be greater than zero"
            )
            Long accountId,

            @RequestParam(required = false)
            TransactionType transactionType,

            @RequestParam(required = false)
            TransactionStatus status,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime toDate,

            @RequestParam(defaultValue = "0")
            @Min(
                    value = 0,
                    message = "Page number must be zero or greater"
            )
            int page,

            @RequestParam(defaultValue = "20")
            @Min(
                    value = 1,
                    message = "Page size must be at least 1"
            )
            @Max(
                    value = 100,
                    message = "Page size must not exceed 100"
            )
            int size,

            @RequestParam(defaultValue = "createdAt")
            String sortBy,

            @RequestParam(defaultValue = "desc")
            String sortDir) {

        return buildTransactionSearchResponse(
                accountId,
                null,
                transactionType,
                status,
                fromDate,
                toDate,
                page,
                size,
                sortBy,
                sortDir
        );
    }

    // ============================================================
    // Customer Transactions
    // ============================================================

    @Operation(
            summary = "Get transactions by customer",
            description = """
                    Retrieves transactions involving any account belonging
                    to the specified customer.

                    Transaction Service obtains the customer's accounts
                    from Account Service before searching transactions.

                    Results support filtering, pagination, and sorting.
                    """
    )
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<
            ApiResponse<PageResponse<TransactionResponse>>>
    getTransactionsByCustomer(

            @PathVariable
            @Positive(
                    message = "Customer ID must be greater than zero"
            )
            Long customerId,

            @RequestParam(required = false)
            TransactionType transactionType,

            @RequestParam(required = false)
            TransactionStatus status,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime toDate,

            @RequestParam(defaultValue = "0")
            @Min(
                    value = 0,
                    message = "Page number must be zero or greater"
            )
            int page,

            @RequestParam(defaultValue = "20")
            @Min(
                    value = 1,
                    message = "Page size must be at least 1"
            )
            @Max(
                    value = 100,
                    message = "Page size must not exceed 100"
            )
            int size,

            @RequestParam(defaultValue = "createdAt")
            String sortBy,

            @RequestParam(defaultValue = "desc")
            String sortDir) {

        return buildTransactionSearchResponse(
                null,
                customerId,
                transactionType,
                status,
                fromDate,
                toDate,
                page,
                size,
                sortBy,
                sortDir
        );
    }

    // ============================================================
    // Submit Transaction
    // ============================================================

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
                    description = """
                            Unique key that prevents duplicate
                            transaction processing
                            """,
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

        TransactionStatus transactionStatus =
                transaction.getTransactionStatus();

        return ResponseEntity
                .status(
                        getSubmissionStatus(
                                transactionStatus
                        )
                )
                .body(
                        new ApiResponse<>(
                                true,
                                getSubmissionMessage(
                                        transactionStatus
                                ),
                                TransactionResponse.from(
                                        transaction
                                )
                        )
                );
    }

    // ============================================================
    // Get Transaction by Reference
    // ============================================================

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

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Transaction retrieved successfully",
                        TransactionResponse.from(transaction)
                )
        );
    }

    // ============================================================
    // Transaction Status Audit History
    // ============================================================

    @Operation(
            summary = "Get transaction status history",
            description = """
                    Retrieves the complete ordered audit history for a
                    transaction.

                    The response includes the previous status, new status,
                    transition source, reason, correlation ID, and
                    transition timestamp.
                    """
    )
    @GetMapping("/{transactionReference}/history")
    public ResponseEntity<
            ApiResponse<List<TransactionStatusHistoryResponse>>>
    getTransactionStatusHistory(

            @Parameter(
                    description = "Unique transaction reference",
                    required = true,
                    example = "572c27e1-ac11-4b41-b407-36f4477034dc"
            )
            @PathVariable
            String transactionReference) {

        List<TransactionStatusHistory> history =
                transactionStatusHistoryService
                        .getTransactionHistory(
                                transactionReference
                        );

        List<TransactionStatusHistoryResponse> response =
                history.stream()
                        .map(
                                TransactionStatusHistoryResponse::from
                        )
                        .toList();

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Transaction status history retrieved successfully",
                        response
                )
        );
    }

    // ============================================================
    // Reconcile Transaction
    // ============================================================

    @Operation(
            summary = "Reconcile transaction",
            description = """
                    Checks the Account Service ledger to determine the
                    outcome of a PROCESSING or
                    RECONCILIATION_REQUIRED transaction.

                    This endpoint verifies the existing balance operation.
                    It does not execute the debit, credit, or transfer
                    again.
                    """
    )
    @PostMapping("/{transactionReference}/reconcile")
    public ResponseEntity<ApiResponse<TransactionResponse>>
    reconcileTransaction(

            @PathVariable
            String transactionReference) {

        Transaction transaction =
                transactionService
                        .reconcileTransaction(
                                transactionReference
                        );

        TransactionStatus transactionStatus =
                transaction.getTransactionStatus();

        return ResponseEntity
                .status(
                        getReconciliationStatus(
                                transactionStatus
                        )
                )
                .body(
                        new ApiResponse<>(
                                true,
                                getReconciliationMessage(
                                        transactionStatus
                                ),
                                TransactionResponse.from(
                                        transaction
                                )
                        )
                );
    }

    // ============================================================
    // Transaction Search Response Builder
    // ============================================================

    private ResponseEntity<
            ApiResponse<PageResponse<TransactionResponse>>>
    buildTransactionSearchResponse(

            Long accountId,
            Long customerId,
            TransactionType transactionType,
            TransactionStatus status,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        Page<Transaction> transactionPage =
                transactionService.getTransactions(
                        accountId,
                        customerId,
                        transactionType,
                        status,
                        fromDate,
                        toDate,
                        page,
                        size,
                        sortBy,
                        sortDir
                );

        List<TransactionResponse> content =
                transactionPage
                        .getContent()
                        .stream()
                        .map(TransactionResponse::from)
                        .toList();

        PageResponse<TransactionResponse> pageResponse =
                new PageResponse<>(
                        content,
                        transactionPage.getNumber(),
                        transactionPage.getSize(),
                        transactionPage.getTotalElements(),
                        transactionPage.getTotalPages(),
                        transactionPage.isFirst(),
                        transactionPage.isLast()
                );

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Transactions retrieved successfully",
                        pageResponse
                )
        );
    }

    // ============================================================
    // Response Messages and Status Codes
    // ============================================================

    private String getSubmissionMessage(
            TransactionStatus status) {

        return switch (status) {

            case COMPLETED ->
                    "Transaction completed successfully";

            case PENDING ->
                    "Transaction recorded; processing is pending";

            case PROCESSING ->
                    "Transaction is currently being processed";

            case FAILED ->
                    "Transaction processing failed";

            case RECONCILIATION_REQUIRED ->
                    "Transaction outcome requires reconciliation";

            case MANUAL_REVIEW ->
                    "Transaction requires manual review";
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
                 RECONCILIATION_REQUIRED,
                 MANUAL_REVIEW ->
                    HttpStatus.ACCEPTED;
        };
    }

    private String getReconciliationMessage(
            TransactionStatus status) {

        return switch (status) {

            case COMPLETED ->
                    "Transaction reconciliation completed successfully";

            case RECONCILIATION_REQUIRED ->
                    "Transaction still requires reconciliation";

            case MANUAL_REVIEW ->
                    "Transaction requires manual review";

            case PROCESSING ->
                    "Transaction reconciliation is still processing";

            case FAILED ->
                    "Transaction processing failed";

            case PENDING ->
                    "Transaction processing is pending";
        };
    }

    private HttpStatus getReconciliationStatus(
            TransactionStatus status) {

        return switch (status) {

            case COMPLETED,
                 FAILED ->
                    HttpStatus.OK;

            case PENDING,
                 PROCESSING,
                 RECONCILIATION_REQUIRED,
                 MANUAL_REVIEW ->
                    HttpStatus.ACCEPTED;
        };
    }
}