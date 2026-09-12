package com.financialplatform.customer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "CUSTOMER_KYC")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerKyc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "KYC_ID")
    private Long kycId;

    @Column(name = "CUSTOMER_ID", nullable = false, unique = true)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "KYC_STATUS", nullable = false, length = 30)
    private KycStatus kycStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "DOCUMENT_TYPE", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "DOCUMENT_NUMBER", nullable = false, length = 100)
    private String documentNumber;

    @Column(name = "VERIFIED_AT")
    private LocalDateTime verifiedAt;

    @Column(name = "LAST_REVIEWED_AT")
    private LocalDateTime lastReviewedAt;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
}