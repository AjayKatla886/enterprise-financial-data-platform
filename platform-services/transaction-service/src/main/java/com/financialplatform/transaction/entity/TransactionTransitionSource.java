package com.financialplatform.transaction.entity;

public enum TransactionTransitionSource {

    TRANSACTION_SUBMISSION,

    TRANSACTION_PROCESSOR,

    RECONCILIATION_API,

    RECONCILIATION_SCHEDULER,

    MANUAL_REVIEW
}