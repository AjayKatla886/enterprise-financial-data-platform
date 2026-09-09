package com.financialplatform.common.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
        boolean success,
        String errorCode,
        String message,
        LocalDateTime timestamp,
        String path
) {
}