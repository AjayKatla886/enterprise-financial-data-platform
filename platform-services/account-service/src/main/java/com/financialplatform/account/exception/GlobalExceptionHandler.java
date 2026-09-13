package com.financialplatform.account.exception;

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

    /*
     * Keep this handler only while AccountNotFoundException is still
     * used somewhere in the account service.
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex,
            HttpServletRequest request) {

        log.warn(
                "Account not found. message={}",
                ex.getMessage()
        );

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                ErrorCode.ACCOUNT_NOT_FOUND,
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(AccountBusinessException.class)
    public ResponseEntity<ErrorResponse> handleAccountBusinessException(
            AccountBusinessException ex,
            HttpServletRequest request) {

        HttpStatus status = mapBusinessErrorStatus(
                ex.getErrorCode()
        );

        log.warn(
                "Account business rule rejected. errorCode={}, status={}, message={}",
                ex.getErrorCode().getCode(),
                status.value(),
                ex.getMessage()
        );

        return buildErrorResponse(
                status,
                ex.getErrorCode(),
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
                                + error.getDefaultMessage()
                )
                .collect(Collectors.joining(", "));

        log.warn(
                "Account request body validation failed. validationErrorCount={}",
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
                .collect(Collectors.joining(", "));

        if (message.isBlank()) {
            message = "Invalid request parameter";
        }

        log.warn(
                "Account request parameter validation failed. violationCount={}",
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
    public ResponseEntity<ErrorResponse> handleInvalidRequestBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn(
                "Invalid account request body. path={}",
                request.getRequestURI()
        );

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                "Invalid request body or unsupported field value",
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        log.warn(
                "Account request parameter type mismatch. parameter={}",
                ex.getName()
        );

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                "Invalid value for parameter: " + ex.getName(),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn(
                "Invalid account request. message={}",
                ex.getMessage()
        );

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.warn(
                "Account database integrity violation. exceptionType={}",
                ex.getClass().getSimpleName()
        );

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                ErrorCode.INVALID_REQUEST,
                "Duplicate or invalid database data",
                request
        );
    }

    @ExceptionHandler(CustomerServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleCustomerServiceUnavailable(
            CustomerServiceUnavailableException ex,
            HttpServletRequest request) {

        log.error(
                "Customer Service dependency is unavailable. message={}",
                ex.getMessage()
        );

        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.CUSTOMER_SERVICE_UNAVAILABLE,
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request) {

        log.error(
                "Unexpected error occurred while processing account request",
                ex
        );

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request
        );
    }

    private HttpStatus mapBusinessErrorStatus(
            ErrorCode errorCode) {

        return switch (errorCode) {

            case ACCOUNT_NOT_FOUND,
                 BALANCE_OPERATION_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;

            case INVALID_REQUEST,
                 VALIDATION_ERROR ->
                    HttpStatus.BAD_REQUEST;

            case DUPLICATE_ACCOUNT_TYPE,
                 INVALID_ACCOUNT_STATE,
                 ACCOUNT_ALREADY_CLOSED,
                 CUSTOMER_KYC_NOT_VERIFIED,
                 INSUFFICIENT_FUNDS,
                 BALANCE_OPERATION_IDEMPOTENCY_CONFLICT ->
                    HttpStatus.CONFLICT;

            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
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
}