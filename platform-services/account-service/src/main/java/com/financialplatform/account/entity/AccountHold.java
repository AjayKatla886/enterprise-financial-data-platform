package com.financialplatform.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ACCOUNT_HOLDS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountHold {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    @Column(name = "ACCOUNT_HOLD_ID")
    private Long accountHoldId;

    @Column(
            name = "HOLD_REFERENCE",
            nullable = false,
            length = 150,
            unique = true
    )
    private String holdReference;

    @Column(
            name = "TRANSACTION_REFERENCE",
            nullable = false,
            length = 36
    )
    private String transactionReference;

    @Column(
            name = "ACCOUNT_ID",
            nullable = false
    )
    private Long accountId;

    @Column(
            name = "AMOUNT",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "HOLD_STATUS",
            nullable = false,
            length = 20
    )
    private AccountHoldStatus holdStatus;

    @Column(
            name = "REQUEST_HASH",
            nullable = false,
            length = 64
    )
    private String requestHash;

    @Column(
            name = "DESCRIPTION",
            length = 255
    )
    private String description;

    @Column(
            name = "EXPIRES_AT",
            nullable = false
    )
    private LocalDateTime expiresAt;

    @Column(
            name = "CREATED_AT",
            nullable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "UPDATED_AT",
            nullable = false
    )
    private LocalDateTime updatedAt;

    @Column(name = "RESOLVED_AT")
    private LocalDateTime resolvedAt;

    /*
     * Protects future hold status transitions from lost updates
     * when multiple processes attempt to capture, release, or
     * expire the same hold concurrently.
     */
    @Version
    @Column(
            name = "VERSION",
            nullable = false
    )
    private Long version;
}