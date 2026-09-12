package com.financialplatform.customer.service;

import com.financialplatform.customer.dto.CustomerKycRequest;
import com.financialplatform.customer.dto.CustomerKycStatusRequest;
import com.financialplatform.customer.entity.*;
import com.financialplatform.customer.exception.CustomerKycNotFoundException;
import com.financialplatform.customer.exception.CustomerNotFoundException;
import com.financialplatform.customer.exception.DuplicateCustomerKycException;
import com.financialplatform.customer.exception.KycCustomerInactiveException;
import com.financialplatform.customer.repository.CustomerKycRepository;
import com.financialplatform.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerKycServiceTest {

    @Mock
    private CustomerKycRepository customerKycRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerKycService customerKycService;

    private Customer activeCustomer;

    @BeforeEach
    void setUp() {
        activeCustomer = new Customer();
        activeCustomer.setCustomerId(1L);
        activeCustomer.setCustomerStatus(CustomerStatus.ACTIVE);
    }

    @Test
    void shouldCreateKycForActiveCustomer() {

        CustomerKycRequest request =
                new CustomerKycRequest(
                        DocumentType.PASSPORT,
                        "P1234567"
                );

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(activeCustomer));

        when(customerKycRepository.existsByCustomerId(1L))
                .thenReturn(false);

        when(customerKycRepository.save(any(CustomerKyc.class)))
                .thenAnswer(invocation -> {
                    CustomerKyc kyc = invocation.getArgument(0);
                    kyc.setKycId(100L);
                    return kyc;
                });

        var response = customerKycService.createKyc(1L, request);

        assertEquals(100L, response.kycId());
        assertEquals(1L, response.customerId());
        assertEquals("PENDING", response.kycStatus());
        assertEquals("PASSPORT", response.documentType());

        verify(customerKycRepository).save(any(CustomerKyc.class));
    }

    @Test
    void shouldRejectKycCreationForInactiveCustomer() {

        activeCustomer.setCustomerStatus(CustomerStatus.INACTIVE);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(activeCustomer));

        assertThrows(
                KycCustomerInactiveException.class,
                () -> customerKycService.createKyc(
                        1L,
                        new CustomerKycRequest(
                                DocumentType.PASSPORT,
                                "P1234567"
                        )
                )
        );

        verifyNoInteractions(customerKycRepository);
    }

    @Test
    void shouldRejectDuplicateKycForCustomer() {

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(activeCustomer));

        when(customerKycRepository.existsByCustomerId(1L))
                .thenReturn(true);

        assertThrows(
                DuplicateCustomerKycException.class,
                () -> customerKycService.createKyc(
                        1L,
                        new CustomerKycRequest(
                                DocumentType.PASSPORT,
                                "P1234567"
                        )
                )
        );

        verify(customerKycRepository, never())
                .save(any());
    }

    @Test
    void shouldThrowExceptionWhenCustomerDoesNotExist() {

        when(customerRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                CustomerNotFoundException.class,
                () -> customerKycService.createKyc(
                        999L,
                        new CustomerKycRequest(
                                DocumentType.PASSPORT,
                                "P1234567"
                        )
                )
        );
    }

    @Test
    void shouldGetKycByCustomerId() {

        CustomerKyc kyc = buildKyc(KycStatus.PENDING);

        when(customerKycRepository.findByCustomerId(1L))
                .thenReturn(Optional.of(kyc));

        var response =
                customerKycService.getKycByCustomerId(1L);

        assertEquals(1L, response.customerId());
        assertEquals("PENDING", response.kycStatus());
    }

    @Test
    void shouldThrowExceptionWhenKycNotFound() {

        when(customerKycRepository.findByCustomerId(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                CustomerKycNotFoundException.class,
                () -> customerKycService
                        .getKycByCustomerId(1L)
        );
    }

    @Test
    void shouldUpdateKycStatusToVerified() {

        CustomerKyc kyc = buildKyc(KycStatus.PENDING);

        when(customerKycRepository.findByCustomerId(1L))
                .thenReturn(Optional.of(kyc));

        when(customerKycRepository.save(any(CustomerKyc.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response =
                customerKycService.updateKycStatus(
                        1L,
                        new CustomerKycStatusRequest(
                                KycStatus.VERIFIED
                        )
                );

        assertEquals("VERIFIED", response.kycStatus());
        assertNotNull(response.verifiedAt());
        assertNotNull(response.lastReviewedAt());
    }

    @Test
    void shouldUpdateKycStatusToRejected() {

        CustomerKyc kyc = buildKyc(KycStatus.PENDING);

        when(customerKycRepository.findByCustomerId(1L))
                .thenReturn(Optional.of(kyc));

        when(customerKycRepository.save(any(CustomerKyc.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response =
                customerKycService.updateKycStatus(
                        1L,
                        new CustomerKycStatusRequest(
                                KycStatus.REJECTED
                        )
                );

        assertEquals("REJECTED", response.kycStatus());
        assertNull(response.verifiedAt());
        assertNotNull(response.lastReviewedAt());
    }

    private CustomerKyc buildKyc(KycStatus status) {

        LocalDateTime now = LocalDateTime.now();

        return CustomerKyc.builder()
                .kycId(10L)
                .customerId(1L)
                .kycStatus(status)
                .documentType(DocumentType.PASSPORT)
                .documentNumber("P1234567")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}