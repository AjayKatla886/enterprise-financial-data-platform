package com.financialplatform.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRANSACTIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TRANSACTION_ID")
    private Long transactionId;

    @Column(
            name = "TRANSACTION_REFERENCE",
            nullable = false,
            length = 36,
            unique = true
    )
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSACTION_TYPE", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "SOURCE_ACCOUNT_ID")
    private Long sourceAccountId;

    @Column(name = "TARGET_ACCOUNT_ID")
    private Long targetAccountId;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSACTION_STATUS", nullable = false, length = 20)
    private TransactionStatus transactionStatus;

    @Column(name = "DESCRIPTION", length = 255)
    private String description;

    @Column(name = "FAILURE_REASON", length = 500)
    private String failureReason;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
}