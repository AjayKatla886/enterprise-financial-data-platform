package com.financialplatform.account.controller;

import com.financialplatform.account.dto.AccountHoldRequest;
import com.financialplatform.account.dto.AccountHoldResponse;
import com.financialplatform.account.entity.AccountHold;
import com.financialplatform.account.service.AccountHoldService;
import com.financialplatform.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/api/v1/accounts")
@RequiredArgsConstructor
@Tag(
        name = "Internal Account Holds",
        description = """
                Internal APIs for reserving account funds and retrieving
                account-hold information
                """
)
public class AccountHoldController {

    private final AccountHoldService accountHoldService;

    @Operation(
            summary = "Create account hold",
            description = """
                    Reserves part of an active account's available balance.

                    Creating a hold does not change the account's ledger
                    balance. It reduces only the available balance.

                    The Hold-Reference header provides idempotency.
                    """
    )
    @PostMapping("/{accountId}/holds")
    public ResponseEntity<
            ApiResponse<AccountHoldResponse>>
    createHold(

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
                    description = "Unique idempotency reference for the hold",
                    required = true,
                    example = "external-transfer-123-hold"
            )
            @RequestHeader(
                    value = "Hold-Reference",
                    required = false
            )
            String holdReference,

            @Valid
            @RequestBody
            AccountHoldRequest request) {

        AccountHold hold =
                accountHoldService.createHold(
                        accountId,
                        holdReference,
                        request
                );

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Account hold created successfully",
                        AccountHoldResponse.from(hold)
                )
        );
    }

    @Operation(
            summary = "Get account hold",
            description = """
                    Retrieves an account hold using its unique
                    hold reference
                    """
    )
    @GetMapping("/holds/{holdReference}")
    public ResponseEntity<
            ApiResponse<AccountHoldResponse>>
    getHold(

            @PathVariable
            String holdReference) {

        AccountHold hold =
                accountHoldService
                        .getByHoldReference(
                                holdReference
                        );

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Account hold retrieved successfully",
                        AccountHoldResponse.from(hold)
                )
        );
    }
}