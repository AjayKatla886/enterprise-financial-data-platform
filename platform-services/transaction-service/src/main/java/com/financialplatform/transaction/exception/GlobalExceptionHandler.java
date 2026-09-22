package com.financialplatform.transaction.exception;

import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransactionBusinessException.class)
    public ResponseEntity<ErrorResponse> handleTransactionBusiness(
            TransactionBusinessException ex,
            HttpServletRequest request) {

        HttpStatus status = mapBusinessErrorStatus(
                ex.getErrorCode()
        );

        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {

            log.error(
                    "Unmapped transaction business error. errorCode={}",
                    ex.getErrorCode(),
                    ex
            );

            return buildErrorResponse(
                    status,
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred",
                    request
            );
        }

        log.warn(
                "Transaction rejected. errorCode={}, status={}, message={}",
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

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleOpenCircuit(
            CallNotPermittedException ex,
            HttpServletRequest request) {

        log.warn(
                "Account Service circuit breaker rejected the request. "
                        + "circuitBreaker={}",
                ex.getCausingCircuitBreakerName()
        );

        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.ACCOUNT_SERVICE_UNAVAILABLE,
                "Account Service is temporarily unavailable",
                request
        );
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAccountServiceUnavailable(
            AccountServiceUnavailableException ex,
            HttpServletRequest request) {

        log.error(
                "Account Service dependency failed",
                ex
        );

        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.ACCOUNT_SERVICE_UNAVAILABLE,
                "Account Service is currently unavailable",
                request
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request) {

        log.warn(
                "Required request header is missing. headerName={}",
                ex.getHeaderName()
        );

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                ex.getHeaderName() + " header is required",
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String message = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn(
                "Transaction request validation failed. errorCount={}",
                ex.getBindingResult().getErrorCount()
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

        log.warn(
                "Transaction parameter validation failed. violationCount={}",
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
    public ResponseEntity<ErrorResponse> handleInvalidBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn(
                "Invalid transaction request body. path={}",
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
                "Transaction parameter type mismatch. parameter={}",
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
                "Invalid transaction request. message={}",
                ex.getMessage()
        );

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request) {

        log.error(
                "Unexpected transaction request failure",
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

            case TRANSACTION_ACCOUNT_NOT_FOUND,
                 TRANSACTION_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;

            case TRANSACTION_ACCOUNT_NOT_ACTIVE,
                 TRANSACTION_IDEMPOTENCY_CONFLICT,
                 INSUFFICIENT_FUNDS ->
                    HttpStatus.CONFLICT;

            case INVALID_REQUEST,
                 VALIDATION_ERROR ->
                    HttpStatus.BAD_REQUEST;

            default ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
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