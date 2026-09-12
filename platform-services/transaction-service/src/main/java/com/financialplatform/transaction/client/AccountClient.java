package com.financialplatform.transaction.client;

import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.common.response.ApiResponse;
import com.financialplatform.transaction.exception.AccountServiceUnavailableException;
import com.financialplatform.transaction.exception.TransactionBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Component
public class AccountClient {

    private final RestClient restClient;

    public AccountClient(
            RestClient.Builder builder,
            @Value("${services.account.base-url}") String baseUrl) {

        this.restClient = builder.clone()
                .baseUrl(baseUrl)
                .build();
    }

    public AccountLookupResponse getAccountById(Long accountId) {

        try {
            ApiResponse<AccountLookupResponse> response = restClient
                    .get()
                    .uri("/api/v1/accounts/{accountId}", accountId)
                    .retrieve()
                    .onStatus(
                            status -> status.value() == 404,
                            (request, responseEntity) -> {
                                throw new TransactionBusinessException(
                                        ErrorCode.TRANSACTION_ACCOUNT_NOT_FOUND,
                                        "Account not found with ID: " + accountId
                                );
                            }
                    )
                    .onStatus(
                            status -> status.isError(),
                            (request, responseEntity) -> {
                                throw new AccountServiceUnavailableException(
                                        "Account Service lookup failed"
                                );
                            }
                    )
                    .body(new ParameterizedTypeReference<
                            ApiResponse<AccountLookupResponse>>() {
                    });

            if (response == null
                    || !response.success()
                    || response.data() == null
                    || !accountId.equals(response.data().accountId())
                    || response.data().accountStatus() == null
                    || response.data().balance() == null) {

                throw new AccountServiceUnavailableException(
                        "Invalid response received from Account Service"
                );
            }

            return response.data();

        } catch (RestClientException ex) {
            throw new AccountServiceUnavailableException(
                    "Unable to retrieve account from Account Service",
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
}