-- ============================================================
-- Enterprise Financial Data Platform
-- Transaction Service - Reconciliation Status Support
-- ============================================================

ALTER TABLE TRANSACTIONS
    MODIFY TRANSACTION_STATUS VARCHAR2(30);