package com.financialplatform.transaction.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "TRANSACTION_STATUS_HISTORY",
        indexes = {
                @Index(
                        name = "IDX_TXN_HISTORY_TRANSACTION",
                        columnList = "TRANSACTION_ID"
                ),
                @Index(
                        name = "IDX_TXN_HISTORY_REFERENCE",
                        columnList = "TRANSACTION_REFERENCE"
                ),
                @Index(
                        name = "IDX_TXN_HISTORY_CREATED_AT",
                        columnList = "CREATED_AT"
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TransactionStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STATUS_HISTORY_ID")
    private Long statusHistoryId;

    @Column(
            name = "TRANSACTION_ID",
            nullable = false
    )
    private Long transactionId;

    @Column(
            name = "TRANSACTION_REFERENCE",
            nullable = false,
            length = 36
    )
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "PREVIOUS_STATUS",
            length = 30
    )
    private TransactionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "NEW_STATUS",
            nullable = false,
            length = 30
    )
    private TransactionStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "TRANSITION_SOURCE",
            nullable = false,
            length = 50
    )
    private TransactionTransitionSource transitionSource;

    @Column(
            name = "TRANSITION_REASON",
            length = 500
    )
    private String transitionReason;

    @Column(
            name = "CORRELATION_ID",
            length = 100
    )
    private String correlationId;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;
}