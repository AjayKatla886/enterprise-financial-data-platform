package com.financialplatform.account.exception;

import com.financialplatform.common.exception.ErrorCode;

public class AccountBusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public AccountBusinessException(
            ErrorCode errorCode,
            String message) {

        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}