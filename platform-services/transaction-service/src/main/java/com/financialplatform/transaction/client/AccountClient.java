package com.financialplatform.transaction.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.common.exception.ErrorResponse;
import com.financialplatform.common.response.ApiResponse;
import com.financialplatform.transaction.exception.AccountServiceUnavailableException;
import com.financialplatform.transaction.exception.TransactionBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Component
public class AccountClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AccountClient(
            RestClient.Builder builder,
            @Value("${services.account.base-url}") String baseUrl) {

        this.restClient = builder.clone()
                .baseUrl(baseUrl)
                .build();

        this.objectMapper =
                new ObjectMapper()
                        .findAndRegisterModules();
    }

    public AccountLookupResponse getAccountById(
            Long accountId) {

        try {
            ApiResponse<AccountLookupResponse> response =
                    restClient
                            .get()
                            .uri(
                                    "/api/v1/accounts/{accountId}",
                                    accountId
                            )
                            .retrieve()
                            .onStatus(
                                    status -> status.value() == 404,
                                    (request, responseEntity) -> {
                                        throw new TransactionBusinessException(
                                                ErrorCode.TRANSACTION_ACCOUNT_NOT_FOUND,
                                                "Account not found with ID: "
                                                        + accountId
                                        );
                                    }
                            )
                            .onStatus(
                                    HttpStatusCode::is5xxServerError,
                                    (request, responseEntity) -> {
                                        throw new AccountServiceUnavailableException(
                                                "Account Service lookup failed"
                                        );
                                    }
                            )
                            .body(
                                    new ParameterizedTypeReference<
                                            ApiResponse<AccountLookupResponse>>() {
                                    }
                            );

            if (response == null
                    || !response.success()
                    || response.data() == null
                    || !accountId.equals(
                    response.data().accountId()
            )
                    || response.data().accountStatus() == null
                    || response.data().balance() == null) {

                throw new AccountServiceUnavailableException(
                        "Invalid response received from Account Service"
                );
            }

            return response.data();

        } catch (TransactionBusinessException |
                 AccountServiceUnavailableException ex) {

            throw ex;

        } catch (RestClientException ex) {

            throw new AccountServiceUnavailableException(
                    "Unable to retrieve account from Account Service",
                    ex
            );
        }
    }

    public AccountBalanceOperationResponse applyBalanceOperation(
            Long accountId,
            String operationReference,
            String transactionReference,
            String operationType,
            BigDecimal amount,
            String description) {

        AccountBalanceOperationRequest requestBody =
                new AccountBalanceOperationRequest(
                        transactionReference,
                        operationType,
                        amount,
                        description
                );

        try {
            ApiResponse<AccountBalanceOperationResponse> response =
                    restClient
                            .post()
                            .uri(
                                    "/internal/api/v1/accounts/{accountId}/balance-operations",
                                    accountId
                            )
                            .header(
                                    "Operation-Reference",
                                    operationReference
                            )
                            .body(requestBody)
                            .retrieve()
                            .onStatus(
                                    HttpStatusCode::is4xxClientError,
                                    (request, responseEntity) ->
                                            handleBalanceBusinessError(
                                                    accountId,
                                                    responseEntity
                                            )
                            )
                            .onStatus(
                                    HttpStatusCode::is5xxServerError,
                                    (request, responseEntity) -> {
                                        throw new AccountServiceUnavailableException(
                                                "Account Service balance operation failed"
                                        );
                                    }
                            )
                            .body(
                                    new ParameterizedTypeReference<
                                            ApiResponse<AccountBalanceOperationResponse>>() {
                                    }
                            );

            if (response == null
                    || !response.success()
                    || response.data() == null
                    || !accountId.equals(
                    response.data().accountId()
            )
                    || !operationReference.equals(
                    response.data()
                            .operationReference()
            )
                    || response.data().balanceAfter() == null) {

                throw new AccountServiceUnavailableException(
                        "Invalid balance-operation response received "
                                + "from Account Service"
                );
            }

            return response.data();

        } catch (TransactionBusinessException |
                 AccountServiceUnavailableException ex) {

            throw ex;

        } catch (RestClientException ex) {

            throw new AccountServiceUnavailableException(
                    "Unable to apply balance operation "
                            + "through Account Service",
                    ex
            );
        }
    }

    public AccountTransferResponse applyTransfer(
            String operationReference,
            String transactionReference,
            Long sourceAccountId,
            Long targetAccountId,
            BigDecimal amount,
            String description) {

        AccountTransferRequest requestBody =
                new AccountTransferRequest(
                        sourceAccountId,
                        targetAccountId,
                        transactionReference,
                        amount,
                        description
                );

        try {
            ApiResponse<AccountTransferResponse> response =
                    restClient
                            .post()
                            .uri(
                                    "/internal/api/v1/accounts/transfers"
                            )
                            .header(
                                    "Operation-Reference",
                                    operationReference
                            )
                            .body(requestBody)
                            .retrieve()
                            .onStatus(
                                    HttpStatusCode::is4xxClientError,
                                    (request, responseEntity) ->
                                            handleBalanceBusinessError(
                                                    sourceAccountId,
                                                    responseEntity
                                            )
                            )
                            .onStatus(
                                    HttpStatusCode::is5xxServerError,
                                    (request, responseEntity) -> {
                                        throw new AccountServiceUnavailableException(
                                                "Account Service transfer failed"
                                        );
                                    }
                            )
                            .body(
                                    new ParameterizedTypeReference<
                                            ApiResponse<AccountTransferResponse>>() {
                                    }
                            );

            validateTransferResponse(
                    response,
                    operationReference,
                    transactionReference,
                    sourceAccountId,
                    targetAccountId
            );

            return response.data();

        } catch (TransactionBusinessException |
                 AccountServiceUnavailableException ex) {

            throw ex;

        } catch (RestClientException ex) {

            throw new AccountServiceUnavailableException(
                    "Unable to apply atomic transfer "
                            + "through Account Service",
                    ex
            );
        }
    }

    private void validateTransferResponse(
            ApiResponse<AccountTransferResponse> response,
            String operationReference,
            String transactionReference,
            Long sourceAccountId,
            Long targetAccountId) {

        if (response == null
                || !response.success()
                || response.data() == null
                || !transactionReference.equals(
                response.data()
                        .transactionReference()
        )
                || response.data().debitOperation() == null
                || response.data().creditOperation() == null
                || !sourceAccountId.equals(
                response.data()
                        .debitOperation()
                        .accountId()
        )
                || !targetAccountId.equals(
                response.data()
                        .creditOperation()
                        .accountId()
        )
                || !(operationReference + "-debit")
                .equals(
                        response.data()
                                .debitOperation()
                                .operationReference()
                )
                || !(operationReference + "-credit")
                .equals(
                        response.data()
                                .creditOperation()
                                .operationReference()
                )) {

            throw new AccountServiceUnavailableException(
                    "Invalid transfer response received "
                            + "from Account Service"
            );
        }
    }

    private void handleBalanceBusinessError(
            Long accountId,
            ClientHttpResponse response) {

        try {
            ErrorResponse errorResponse =
                    objectMapper.readValue(
                            response.getBody(),
                            ErrorResponse.class
                    );

            String downstreamCode =
                    errorResponse.errorCode();

            String downstreamMessage =
                    errorResponse.message();

            if (ErrorCode.ACCOUNT_NOT_FOUND
                    .getCode()
                    .equals(downstreamCode)) {

                throw new TransactionBusinessException(
                        ErrorCode.TRANSACTION_ACCOUNT_NOT_FOUND,
                        downstreamMessage
                );
            }

            if (ErrorCode.INVALID_ACCOUNT_STATE
                    .getCode()
                    .equals(downstreamCode)) {

                throw new TransactionBusinessException(
                        ErrorCode.TRANSACTION_ACCOUNT_NOT_ACTIVE,
                        downstreamMessage
                );
            }

            if (ErrorCode.INSUFFICIENT_FUNDS
                    .getCode()
                    .equals(downstreamCode)) {

                throw new TransactionBusinessException(
                        ErrorCode.INSUFFICIENT_FUNDS,
                        downstreamMessage
                );
            }

            if (ErrorCode.BALANCE_OPERATION_IDEMPOTENCY_CONFLICT
                    .getCode()
                    .equals(downstreamCode)) {

                throw new TransactionBusinessException(
                        ErrorCode.TRANSACTION_IDEMPOTENCY_CONFLICT,
                        downstreamMessage
                );
            }

            throw new AccountServiceUnavailableException(
                    "Account Service rejected the operation"
            );

        } catch (TransactionBusinessException |
                 AccountServiceUnavailableException ex) {

            throw ex;

        } catch (Exception ex) {

            throw new AccountServiceUnavailableException(
                    "Unable to read Account Service error response "
                            + "for account ID: "
                            + accountId,
                    ex
            );
        }
    }

    public record AccountLookupResponse(
            Long accountId,
            String accountNumber,
            Long customerId,
            String accountType,
            BigDecimal balance,
            String accountStatus,
            String createdAt,
            String updatedAt
    ) {
    }

    private record AccountBalanceOperationRequest(
            String transactionReference,
            String operationType,
            BigDecimal amount,
            String description
    ) {
    }

    public record AccountBalanceOperationResponse(
            Long balanceOperationId,
            String operationReference,
            String transactionReference,
            Long accountId,
            String operationType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String description,
            String createdAt
    ) {
    }

    private record AccountTransferRequest(
            Long sourceAccountId,
            Long targetAccountId,
            String transactionReference,
            BigDecimal amount,
            String description
    ) {
    }

    public record AccountTransferResponse(
            String transactionReference,
            AccountBalanceOperationResponse debitOperation,
            AccountBalanceOperationResponse creditOperation
    ) {
    }
    public boolean balanceOperationExists(
            String operationReference) {

        try {
            ApiResponse<BalanceOperationLookupResponse> response =
                    restClient
                            .get()
                            .uri(
                                    "/internal/api/v1/accounts/"
                                            + "balance-operations/{operationReference}",
                                    operationReference
                            )
                            .retrieve()
                            .onStatus(
                                    status -> status.value() == 404,
                                    (request, responseEntity) -> {
                                        throw new BalanceOperationNotFoundException();
                                    }
                            )
                            .body(
                                    new ParameterizedTypeReference<
                                            ApiResponse<
                                                    BalanceOperationLookupResponse>>() {
                                    }
                            );

            if (response == null
                    || !response.success()
                    || response.data() == null) {

                throw new AccountServiceUnavailableException(
                        "Invalid response received from Account Service"
                );
            }

            return operationReference.equals(
                    response.data().operationReference()
            );

        } catch (BalanceOperationNotFoundException ex) {

            return false;

        } catch (AccountServiceUnavailableException ex) {

            throw ex;

        } catch (RestClientException ex) {

            throw new AccountServiceUnavailableException(
                    "Unable to verify balance operation with Account Service",
                    ex
            );
        }
    }

    public boolean transferOperationExists(
            String transferOperationReference) {

        boolean debitExists =
                balanceOperationExists(
                        transferOperationReference + "-debit"
                );

        boolean creditExists =
                balanceOperationExists(
                        transferOperationReference + "-credit"
                );

        if (debitExists != creditExists) {
            throw new AccountServiceUnavailableException(
                    "Incomplete transfer ledger state detected"
            );
        }

        return debitExists;
    }

    public record BalanceOperationLookupResponse(
            Long balanceOperationId,
            String operationReference,
            String transactionReference,
            Long accountId,
            String operationType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String description,
            String createdAt
    ) {
    }

    private static class BalanceOperationNotFoundException
            extends RuntimeException {
    }
}