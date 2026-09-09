package com.financialplatform.customer.exception;

public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException(Long addressId) {
        super("Customer address not found with ID: " + addressId);
    }
}