package com.financialplatform.customer.dto;

import com.financialplatform.customer.entity.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomerKycRequest(

        @NotNull(message = "Document type is required")
        DocumentType documentType,

        @NotBlank(message = "Document number is required")
        @Size(max = 100, message = "Document number must not exceed 100 characters")
        String documentNumber
) {
}