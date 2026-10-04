package com.financialplatform.account.entity;

public enum AccountHoldStatus {

    /*
     * Funds are reserved and reduce the account's available balance.
     */
    ACTIVE,

    /*
     * The reserved funds were converted into a completed debit.
     */
    CAPTURED,

    /*
     * The hold was cancelled and the reserved funds became
     * available again.
     */
    RELEASED,

    /*
     * The hold reached its expiration time without being captured.
     */
    EXPIRED
}