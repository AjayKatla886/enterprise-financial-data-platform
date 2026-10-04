package com.financialplatform.account.entity;

public enum LedgerIntegrityStatus {

    /*
     * Every operation is mathematically correct, the ledger chain is
     * continuous, and the final ledger balance matches the account balance.
     */
    VALID,

    /*
     * The account has no balance-operation records. This can be valid only
     * when the account's stored balance is zero.
     */
    NO_ACTIVITY,

    /*
     * One or more operation calculations are incorrect, the ledger chain
     * contains a gap, or the final ledger balance does not match the account.
     */
    INVALID
}