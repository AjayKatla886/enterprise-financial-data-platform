package com.financialplatform.account.client;

import com.financialplatform.account.exception.CustomerServiceUnavailableException;
import com.financialplatform.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class CustomerClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${services.customer.base-url}")
    private String customerServiceBaseUrl;

    public CustomerLookupResponse getCustomerById(Long customerId) {

        try {

            ApiResponse<CustomerLookupResponse> response =
                    restClientBuilder
                            .baseUrl(customerServiceBaseUrl)
                            .build()
                            .get()
                            .uri("/api/v1/customers/{customerId}", customerId)
                            .retrieve()
                            .onStatus(
                                    status -> status.value() == 404,
                                    (request, responseEntity) -> {
                                        throw new IllegalArgumentException(
                                                "Customer not found with ID: " + customerId
                                        );
                                    }
                            )
                            .onStatus(
                                    HttpStatusCode::is5xxServerError,
                                    (request, responseEntity) -> {
                                        throw new CustomerServiceUnavailableException(
                                                "Customer Service is currently unavailable"
                                        );
                                    }
                            )
                            .body(
                                    new ParameterizedTypeReference<
                                            ApiResponse<CustomerLookupResponse>>() {
                                    }
                            );

            if (response == null || response.data() == null) {
                throw new CustomerServiceUnavailableException(
                        "Invalid response received from Customer Service"
                );
            }

            return response.data();

        } catch (ResourceAccessException ex) {

            throw new CustomerServiceUnavailableException(
                    "Customer Service is currently unavailable"
            );
        }
    }

    public record CustomerLookupResponse(
            Long customerId,
            String customerNumber,
            String firstName,
            String lastName,
            String customerStatus
    ) {
    }
    private record CustomerKycData(
            Long kycId,
            Long customerId,
            String kycStatus,
            String documentType,
            String documentNumber,
            String verifiedAt,
            String lastReviewedAt,
            String createdAt,
            String updatedAt
    ) {
    }

    private record CustomerKycApiResponse(
            boolean success,
            String message,
            CustomerKycData data
    ) {
    }
    public boolean isCustomerKycVerified(Long customerId) {

        try {

            CustomerKycApiResponse response =
                    restClientBuilder
                            .baseUrl(customerServiceBaseUrl)
                            .build()
                            .get()
                            .uri("/api/v1/customers/{customerId}/kyc", customerId)
                            .retrieve()
                            .body(CustomerKycApiResponse.class);

            if (response == null || response.data() == null) {
                return false;
            }

            return "VERIFIED".equalsIgnoreCase(response.data().kycStatus());

        } catch (HttpClientErrorException.NotFound ex) {

            return false;

        } catch (HttpServerErrorException ex) {

            throw new CustomerServiceUnavailableException(
                    "Customer Service is currently unavailable"
            );

        } catch (ResourceAccessException ex) {

            throw new CustomerServiceUnavailableException(
                    "Customer Service is currently unavailable"
            );
        }
    }
}