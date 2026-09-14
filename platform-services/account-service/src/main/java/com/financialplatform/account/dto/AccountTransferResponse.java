package com.financialplatform.account.dto;

public record AccountTransferResponse(

        String transactionReference,
        BalanceOperationResponse debitOperation,
        BalanceOperationResponse creditOperation

) {
}