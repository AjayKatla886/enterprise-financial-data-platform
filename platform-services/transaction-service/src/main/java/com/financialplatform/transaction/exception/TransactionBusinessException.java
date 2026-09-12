package com.financialplatform.transaction.exception;

import com.financialplatform.common.exception.ErrorCode;

public class TransactionBusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public TransactionBusinessException(
            ErrorCode errorCode,
            String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}