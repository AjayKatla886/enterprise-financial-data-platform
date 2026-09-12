package com.financialplatform.customer.exception;

public class KycCustomerInactiveException extends RuntimeException {

    public KycCustomerInactiveException(String message) {
        super(message);
    }
}