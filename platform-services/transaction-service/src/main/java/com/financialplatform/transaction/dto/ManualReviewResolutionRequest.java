package com.financialplatform.transaction.dto;

import com.financialplatform.transaction.entity.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualReviewResolutionRequest(

        @NotNull(
                message = "Resolution status is required"
        )
        @Schema(
                description = "Final status selected after manual investigation",
                allowableValues = {
                        "COMPLETED",
                        "FAILED"
                },
                example = "COMPLETED"
        )
        TransactionStatus resolutionStatus,

        @NotBlank(
                message = "Manual review reason is required"
        )
        @Size(
                max = 500,
                message = "Manual review reason must not exceed 500 characters"
        )
        @Schema(
                description = "Explanation supporting the manual-review decision",
                example = "Account Service ledger confirms that the credit operation completed"
        )
        String reason
) {
}