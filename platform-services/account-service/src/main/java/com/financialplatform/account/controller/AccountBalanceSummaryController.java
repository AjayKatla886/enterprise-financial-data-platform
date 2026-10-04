package com.financialplatform.account.controller;

import com.financialplatform.account.dto.AccountBalanceSummaryResponse;
import com.financialplatform.account.service.AccountHoldService;
import com.financialplatform.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(
        name = "Account Balance Summary",
        description = """
                APIs for retrieving ledger, held, and available
                account balances
                """
)
public class AccountBalanceSummaryController {

    private final AccountHoldService accountHoldService;

    @Operation(
            summary = "Get account balance summary",
            description = """
                    Returns the account ledger balance, total active and
                    unexpired hold amount, and available balance.

                    Available balance is calculated as:

                    ledger balance - active hold amount
                    """
    )
    @GetMapping("/{accountId}/balance-summary")
    public ResponseEntity<
            ApiResponse<AccountBalanceSummaryResponse>>
    getBalanceSummary(

            @Parameter(
                    description = "Account ID",
                    required = true,
                    example = "21"
            )
            @PathVariable
            @Positive(
                    message = "Account ID must be greater than zero"
            )
            Long accountId) {

        AccountBalanceSummaryResponse balanceSummary =
                accountHoldService
                        .getBalanceSummary(accountId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Account balance summary retrieved successfully",
                        balanceSummary
                )
        );
    }
}