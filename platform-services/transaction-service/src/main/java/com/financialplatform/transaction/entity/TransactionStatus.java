package com.financialplatform.transaction.entity;

public enum TransactionStatus {

    /**
     * Transaction request was recorded but execution has not started.
     */
    PENDING,

    /**
     * Transaction execution has started and Account Service
     * may be processing the balance operation.
     */
    PROCESSING,

    /**
     * Transaction and its balance operation completed successfully.
     */
    COMPLETED,

    /**
     * Transaction was rejected by a confirmed business rule.
     */
    FAILED,

    /**
     * Transaction Service could not confirm whether Account Service
     * completed the balance operation.
     */
    RECONCILIATION_REQUIRED,

    /**
     * automatic reconciliation reached its maximum attempts without confirming the outcome.
     */
    MANUAL_REVIEW
}