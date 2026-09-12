package com.financialplatform.customer.exception;

import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);

        when(request.getRequestURI())
                .thenReturn("/api/v1/customers/999999");
    }

    @Test
    void shouldReturnCus001WhenCustomerNotFound() {

        CustomerBusinessException exception =
                new CustomerBusinessException(
                        ErrorCode.CUSTOMER_NOT_FOUND,
                        "Customer not found with ID: 999999"
                );

        ResponseEntity<ErrorResponse> response =
                handler.handleCustomerBusinessException(exception, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());

        assertFalse(response.getBody().success());
        assertEquals(
                ErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                response.getBody().errorCode()
        );
        assertEquals(
                "/api/v1/customers/999999",
                response.getBody().path()
        );
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void shouldReturnCus005WhenAddressNotFound() {

        when(request.getRequestURI())
                .thenReturn("/api/v1/customers/1/addresses/999");

        CustomerBusinessException exception =
                new CustomerBusinessException(
                        ErrorCode.ADDRESS_NOT_FOUND,
                        "Address not found with ID: 999"
                );

        ResponseEntity<ErrorResponse> response =
                handler.handleCustomerBusinessException(exception, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals(
                ErrorCode.ADDRESS_NOT_FOUND.getCode(),
                response.getBody().errorCode()
        );
        assertEquals(
                "/api/v1/customers/1/addresses/999",
                response.getBody().path()
        );
    }

    @Test
    void shouldReturnCus003WhenDuplicateCustomer() {

        CustomerBusinessException exception =
                new CustomerBusinessException(
                        ErrorCode.DUPLICATE_CUSTOMER,
                        "Customer already exists"
                );

        ResponseEntity<ErrorResponse> response =
                handler.handleCustomerBusinessException(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals(
                ErrorCode.DUPLICATE_CUSTOMER.getCode(),
                response.getBody().errorCode()
        );
    }

    @Test
    void shouldReturnVal002ForIllegalArgument() {

        IllegalArgumentException exception =
                new IllegalArgumentException(
                        "Invalid customer request"
                );

        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgument(
                        exception,
                        request
                );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
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