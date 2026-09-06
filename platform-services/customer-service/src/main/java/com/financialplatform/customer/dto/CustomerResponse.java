package com.financialplatform.customer.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CustomerResponse(
        Long customerId,
        String customerNumber,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        LocalDate dateOfBirth,
        String customerStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}