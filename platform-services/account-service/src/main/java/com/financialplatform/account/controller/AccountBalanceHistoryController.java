package com.financialplatform.account.controller;

import com.financialplatform.account.dto.BalanceOperationResponse;
import com.financialplatform.account.entity.BalanceOperationType;
import com.financialplatform.account.service.BalanceOperationService;
import com.financialplatform.common.response.ApiResponse;
import com.financialplatform.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Validated
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(
        name = "Account Balance History",
        description = """
                APIs for retrieving paginated account balance-operation
                history with optional operation-type and date filters
                """
)
public class AccountBalanceHistoryController {

    private final BalanceOperationService balanceOperationService;

    @Operation(
            summary = "Get account balance-operation history",
            description = """
                    Retrieves the debit, credit, and reversal ledger history
                    for an account.

                    Results are returned in descending creation-time order,
                    so the newest balance operation appears first.

                    Optional filters can restrict results by operation type
                    and creation-date range.
                    """
    )
    @GetMapping("/{accountId}/balance-operations")
    public ResponseEntity<
            ApiResponse<PageResponse<BalanceOperationResponse>>>
    getAccountBalanceOperations(

            @Parameter(
                    description = "Account ID",
                    required = true,
                    example = "21"
            )
            @PathVariable
            @Positive(
                    message = "Account ID must be greater than zero"
            )
            Long accountId,

            @Parameter(
                    description = "Optional operation type filter",
                    example = "DEBIT"
            )
            @RequestParam(required = false)
            BalanceOperationType operationType,

            @Parameter(
                    description = """
                            Optional inclusive start date and time.
                            Use ISO-8601 format.
                            """,
                    example = "2026-09-01T00:00:00"
            )
            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime fromDate,

            @Parameter(
                    description = """
                            Optional inclusive end date and time.
                            Use ISO-8601 format.
                            """,
                    example = "2026-10-03T23:59:59"
            )
            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime toDate,

            @Parameter(
                    description = "Zero-based page number",
                    example = "0"
            )
            @RequestParam(defaultValue = "0")
            @Min(
                    value = 0,
                    message = "Page number must not be negative"
            )
            int page,

            @Parameter(
                    description = "Number of operations per page",
                    example = "20"
            )
            @RequestParam(defaultValue = "20")
            @Min(
                    value = 1,
                    message = "Page size must be at least 1"
            )
            @Max(
                    value = 100,
                    message = "Page size must not exceed 100"
            )
            int size) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Direction.DESC,
                        "createdAt"
                ).and(
                        Sort.by(
                                Sort.Direction.DESC,
                                "balanceOperationId"
                        )
                )
        );

        PageResponse<BalanceOperationResponse> history =
                balanceOperationService
                        .getAccountBalanceOperations(
                                accountId,
                                operationType,
                                fromDate,
                                toDate,
                                pageable
                        );

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Account balance history retrieved successfully",
                        history
                )
        );
    }
}