-- ============================================================
-- Transaction Service
-- Automated Reconciliation Tracking
-- ============================================================

ALTER TABLE TRANSACTIONS
    ADD (
        RECONCILIATION_ATTEMPTS NUMBER(10) DEFAULT 0 NOT NULL,
        LAST_RECONCILIATION_AT TIMESTAMP
    );

ALTER TABLE TRANSACTIONS
DROP CONSTRAINT CK_TRANSACTIONS_STATUS;

ALTER TABLE TRANSACTIONS
    ADD CONSTRAINT CK_TRANSACTIONS_STATUS
        CHECK (
            TRANSACTION_STATUS IN (
                                   'PENDING',
                                   'PROCESSING',
                                   'COMPLETED',
                                   'FAILED',
                                   'RECONCILIATION_REQUIRED',
                                   'MANUAL_REVIEW'
                )
            );

CREATE INDEX IDX_TXN_RECONCILIATION
    ON TRANSACTIONS (
                     TRANSACTION_STATUS,
                     UPDATED_AT
        );