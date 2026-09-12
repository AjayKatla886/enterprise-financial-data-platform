package com.financialplatform.customer.exception;

import com.financialplatform.common.exception.ErrorCode;

public class CustomerBusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomerBusinessException(
            ErrorCode errorCode,
            String message) {

        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}