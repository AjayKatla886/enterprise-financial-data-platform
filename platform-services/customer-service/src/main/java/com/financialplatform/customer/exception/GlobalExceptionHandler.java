package com.financialplatform.customer.exception;

import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCustomerNotFound(
            CustomerNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Customer not found exception. message={}", ex.getMessage());

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                ErrorCode.CUSTOMER_NOT_FOUND,
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(AddressNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAddressNotFound(
            AddressNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Customer address not found. message={}", ex.getMessage());

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                ErrorCode.ADDRESS_NOT_FOUND,
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(DuplicateCustomerException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateCustomer(
            DuplicateCustomerException ex,
            HttpServletRequest request) {

        log.warn(
                "Duplicate customer operation rejected. message={}",
                ex.getMessage()
        );

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                ErrorCode.DUPLICATE_CUSTOMER,
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("Invalid request. message={}", ex.getMessage());

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField()
                                + ": "
                                + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn(
                "Request body validation failed. validationErrorCount={}",
                ex.getBindingResult().getFieldErrorCount()
        );

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                message,
                request
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        String message = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("Invalid request parameter");

        log.warn(
                "Request parameter validation failed. violationCount={}",
                ex.getConstraintViolations().size()
        );

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                message,
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn(
                "Invalid request body. Unable to deserialize request payload."
        );

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                "Invalid request body or unsupported field value",
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.warn(
                "Database integrity violation occurred. exceptionType={}",
                ex.getClass().getSimpleName()
        );

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                ErrorCode.INVALID_REQUEST,
                "Duplicate or invalid database data",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request) {

        log.error(
                "Unexpected error occurred while processing customer request",
                ex
        );

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request
        );
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            HttpServletRequest request) {

        ErrorResponse response = new ErrorResponse(
                false,
                errorCode.getCode(),
                message,
                LocalDateTime.now(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }
    @ExceptionHandler(CustomerKycNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCustomerKycNotFound(
            CustomerKycNotFoundException ex,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                ErrorCode.KYC_NOT_FOUND,
                ex.getMessage(),
                request
        );
    }
    @ExceptionHandler(DuplicateCustomerKycException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateCustomerKyc(
            DuplicateCustomerKycException ex,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                ErrorCode.DUPLICATE_CUSTOMER_KYC,
                ex.getMessage(),
                request
        );
    }
    @ExceptionHandler(KycCustomerInactiveException.class)
    public ResponseEntity<ErrorResponse> handleKycCustomerInactive(
            KycCustomerInactiveException ex,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                ErrorCode.KYC_CUSTOMER_INACTIVE,
                ex.getMessage(),
                request
        );
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String message = "Invalid value for parameter: " + ex.getName();

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                message,
                request
        );
    }
}