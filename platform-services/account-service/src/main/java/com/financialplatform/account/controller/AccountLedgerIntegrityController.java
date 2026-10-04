package com.financialplatform.account.controller;

import com.financialplatform.account.dto.LedgerIntegrityResponse;
import com.financialplatform.account.service.AccountLedgerIntegrityService;
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
@RequestMapping("/internal/api/v1/accounts")
@RequiredArgsConstructor
@Tag(
        name = "Internal Account Ledger Integrity",
        description = """
                Internal operational APIs for detecting account-balance
                and ledger-integrity inconsistencies
                """
)
public class AccountLedgerIntegrityController {

    private final AccountLedgerIntegrityService
            accountLedgerIntegrityService;

    @Operation(
            summary = "Check account ledger integrity",
            description = """
                    Audits an account's complete balance-operation ledger.

                    The audit validates every debit and credit calculation,
                    checks continuity between consecutive ledger operations,
                    and compares the final ledger balance with the balance
                    stored on the account.

                    This endpoint is read-only and never automatically changes
                    account balances or ledger records.
                    """
    )
    @GetMapping("/{accountId}/ledger-integrity")
    public ResponseEntity<
            ApiResponse<LedgerIntegrityResponse>>
    checkLedgerIntegrity(

            @Parameter(
                    description = "Account ID to audit",
                    required = true,
                    example = "21"
            )
            @PathVariable
            @Positive(
                    message = "Account ID must be greater than zero"
            )
            Long accountId) {

        LedgerIntegrityResponse integrityResponse =
                accountLedgerIntegrityService
                        .checkLedgerIntegrity(accountId);

        String message =
                switch (integrityResponse.integrityStatus()) {

                    case VALID ->
                            "Account ledger integrity verified";

                    case NO_ACTIVITY ->
                            "Account has no ledger activity";

                    case INVALID ->
                            "Account ledger integrity issues detected";
                };

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        message,
                        integrityResponse
                )
        );
    }
}