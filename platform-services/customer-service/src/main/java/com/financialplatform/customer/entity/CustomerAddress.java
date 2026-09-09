package com.financialplatform.customer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "CUSTOMER_ADDRESSES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ADDRESS_ID")
    private Long addressId;

    @Column(name = "CUSTOMER_ID", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ADDRESS_TYPE", nullable = false, length = 20)
    private AddressType addressType;

    @Column(name = "ADDRESS_LINE_1", nullable = false, length = 150)
    private String addressLine1;

    @Column(name = "ADDRESS_LINE_2", length = 150)
    private String addressLine2;

    @Column(name = "CITY", nullable = false, length = 100)
    private String city;

    @Column(name = "STATE", nullable = false, length = 50)
    private String state;

    @Column(name = "POSTAL_CODE", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "COUNTRY", nullable = false, length = 100)
    private String country;

    @Column(name = "IS_CURRENT", nullable = false)
    private boolean current;

    @Column(name = "VALID_FROM", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "VALID_TO")
    private LocalDateTime validTo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
}