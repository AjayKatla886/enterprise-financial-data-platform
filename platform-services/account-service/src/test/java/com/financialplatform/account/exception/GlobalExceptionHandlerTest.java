package com.financialplatform.account.exception;

import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);

        when(request.getRequestURI())
                .thenReturn("/api/v1/accounts/999999");
    }

    @Test
    void shouldReturnAcc001WhenAccountNotFound() {

        AccountNotFoundException exception =
                new AccountNotFoundException(999999L);

        ResponseEntity<ErrorResponse> response =
                handler.handleAccountNotFound(
                        exception,
                        request
                );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());

        assertFalse(response.getBody().success());

        assertEquals(
                ErrorCode.ACCOUNT_NOT_FOUND.getCode(),
                response.getBody().errorCode()
        );

        assertEquals(
                "/api/v1/accounts/999999",
                response.getBody().path()
        );

        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void shouldReturnDep001WhenCustomerServiceUnavailable() {

        when(request.getRequestURI())
                .thenReturn("/api/v1/accounts");

        CustomerServiceUnavailableException exception =
                new CustomerServiceUnavailableException(
                        "Customer Service is currently unavailable"
                );

        ResponseEntity<ErrorResponse> response =
                handler.handleCustomerServiceUnavailable(
                        exception,
                        request
                );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                ErrorCode.CUSTOMER_SERVICE_UNAVAILABLE.getCode(),
                response.getBody().errorCode()
        );

        assertEquals(
                "Customer Service is currently unavailable",
                response.getBody().message()
        );

        assertEquals(
                "/api/v1/accounts",
                response.getBody().path()
        );
    }

    @Test
    void shouldReturnVal002ForIllegalArgument() {

        IllegalArgumentException exception =
                new IllegalArgumentException(
                        "Invalid account operation"
                );

        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgument(
                        exception,
                        request
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                ErrorCode.INVALID_REQUEST.getCode(),
                response.getBody().errorCode()
        );
    }

    @Test
    void shouldReturnSys001ForUnexpectedException() {

        RuntimeException exception =
                new RuntimeException("Unexpected failure");

        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpectedException(
                        exception,
                        request
                );

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                response.getBody().errorCode()
        );

        assertEquals(
                "An unexpected error occurred",
                response.getBody().message()
        );
    }
}