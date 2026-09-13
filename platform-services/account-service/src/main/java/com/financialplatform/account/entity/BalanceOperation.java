package com.financialplatform.account.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "ACCOUNT_BALANCE_OPERATIONS",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_ACCOUNT_BALANCE_OPERATION_REF",
                        columnNames = "OPERATION_REFERENCE"
                )
        },
        indexes = {
                @Index(
                        name = "IDX_BALANCE_OPERATION_ACCOUNT",
                        columnList = "ACCOUNT_ID"
                ),
                @Index(
                        name = "IDX_BALANCE_OPERATION_TRANSACTION",
                        columnList = "TRANSACTION_REFERENCE"
                ),
                @Index(
                        name = "IDX_BALANCE_OPERATION_CREATED",
                        columnList = "CREATED_AT"
                )
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BALANCE_OPERATION_ID")
    private Long balanceOperationId;

    @Column(
            name = "OPERATION_REFERENCE",
            nullable = false,
            length = 150,
            updatable = false
    )
    private String operationReference;

    @Column(
            name = "TRANSACTION_REFERENCE",
            nullable = false,
            length = 36,
            updatable = false
    )
    private String transactionReference;

    @Column(
            name = "ACCOUNT_ID",
            nullable = false,
            updatable = false
    )
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "OPERATION_TYPE",
            nullable = false,
            length = 20,
            updatable = false
    )
    private BalanceOperationType operationType;

    @Column(
            name = "AMOUNT",
            nullable = false,
            precision = 19,
            scale = 2,
            updatable = false
    )
    private BigDecimal amount;

    @Column(
            name = "BALANCE_BEFORE",
            nullable = false,
            precision = 19,
            scale = 2,
            updatable = false
    )
    private BigDecimal balanceBefore;

    @Column(
            name = "BALANCE_AFTER",
            nullable = false,
            precision = 19,
            scale = 2,
            updatable = false
    )
    private BigDecimal balanceAfter;

    @Column(
            name = "REQUEST_HASH",
            nullable = false,
            length = 64,
            updatable = false
    )
    private String requestHash;

    @Column(
            name = "DESCRIPTION",
            length = 255,
            updatable = false
    )
    private String description;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;
}