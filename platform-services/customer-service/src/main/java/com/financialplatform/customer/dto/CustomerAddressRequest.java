package com.financialplatform.customer.dto;

import com.financialplatform.customer.entity.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomerAddressRequest(

        @NotNull(message = "Address type is required")
        AddressType addressType,

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 150, message = "Address line 1 must not exceed 150 characters")
        String addressLine1,

        @Size(max = 150, message = "Address line 2 must not exceed 150 characters")
        String addressLine2,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 50, message = "State must not exceed 50 characters")
        String state,

        @NotBlank(message = "Postal code is required")
        @Size(max = 20, message = "Postal code must not exceed 20 characters")
        String postalCode,

        @NotBlank(message = "Country is required")
        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country
) {
}