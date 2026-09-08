package com.financialplatform.account.controller;

import com.financialplatform.account.dto.AccountRequest;
import com.financialplatform.account.dto.AccountResponse;
import com.financialplatform.account.service.AccountService;
import com.financialplatform.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.financialplatform.account.dto.AccountStatusRequest;
import com.financialplatform.common.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody AccountRequest request) {

        AccountResponse accountResponse =
                accountService.createAccount(request);

        ApiResponse<AccountResponse> response =
                new ApiResponse<>(
                        true,
                        "Account created successfully",
                        accountResponse
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(
            @PathVariable Long accountId) {

        AccountResponse accountResponse =
                accountService.getAccountById(accountId);

        ApiResponse<AccountResponse> response =
                new ApiResponse<>(
                        true,
                        "Account retrieved successfully",
                        accountResponse
                );

        return ResponseEntity.ok(response);
    }
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAccountsByCustomerId(
            @PathVariable Long customerId) {

        List<AccountResponse> accounts =
                accountService.getAccountsByCustomerId(customerId);

        ApiResponse<List<AccountResponse>> response =
                new ApiResponse<>(
                        true,
                        "Accounts retrieved successfully",
                        accounts
                );

        return ResponseEntity.ok(response);
    }
    @PatchMapping("/{accountId}/status")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccountStatus(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountStatusRequest request) {

        AccountResponse accountResponse =
                accountService.updateAccountStatus(accountId, request);

        ApiResponse<AccountResponse> response =
                new ApiResponse<>(
                        true,
                        "Account status updated successfully",
                        accountResponse
                );

        return ResponseEntity.ok(response);
    }
    @PatchMapping("/{accountId}/close")
    public ResponseEntity<ApiResponse<AccountResponse>> closeAccount(
            @PathVariable Long accountId) {

        AccountResponse accountResponse =
                accountService.closeAccount(accountId);

        ApiResponse<AccountResponse> response =
                new ApiResponse<>(
                        true,
                        "Account closed successfully",
                        accountResponse
                );

        return ResponseEntity.ok(response);
    }
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AccountResponse>>> getAccounts(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be 0 or greater")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int size,
            @RequestParam(defaultValue = "accountId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        PageResponse<AccountResponse> accounts =
                accountService.getAccounts(
                        customerId,
                        accountType,
                        status,
                        page,
                        size,
                        sortBy,
                        sortDir
                );

        ApiResponse<PageResponse<AccountResponse>> response =
                new ApiResponse<>(
                        true,
                        "Accounts retrieved successfully",
                        accounts
                );

        return ResponseEntity.ok(response);
    }
}