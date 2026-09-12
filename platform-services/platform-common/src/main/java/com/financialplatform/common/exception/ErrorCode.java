package com.financialplatform.common.exception;

public enum ErrorCode {

    // Customer Service
    CUSTOMER_NOT_FOUND("CUS-001"),
    CUSTOMER_INACTIVE("CUS-002"),
    DUPLICATE_CUSTOMER("CUS-003"),
    DUPLICATE_ADDRESS("CUS-004"),
    ADDRESS_NOT_FOUND("CUS-005"),
    KYC_NOT_FOUND("KYC-001"),
    DUPLICATE_CUSTOMER_KYC("KYC-002"),
    KYC_CUSTOMER_INACTIVE("KYC-003"),

    // Account Service
    ACCOUNT_NOT_FOUND("ACC-001"),
    DUPLICATE_ACCOUNT_TYPE("ACC-002"),
    INVALID_ACCOUNT_STATE("ACC-003"),
    ACCOUNT_ALREADY_CLOSED("ACC-004"),

    // Downstream Dependencies
    CUSTOMER_SERVICE_UNAVAILABLE("DEP-001"),

    // Request Validation
    VALIDATION_ERROR("VAL-001"),
    INVALID_REQUEST("VAL-002"),

    // System
    INTERNAL_SERVER_ERROR("SYS-001");



    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}