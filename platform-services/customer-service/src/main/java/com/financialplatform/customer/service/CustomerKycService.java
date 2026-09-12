package com.financialplatform.customer.service;

import com.financialplatform.customer.dto.CustomerKycRequest;
import com.financialplatform.customer.dto.CustomerKycResponse;
import com.financialplatform.customer.dto.CustomerKycStatusRequest;
import com.financialplatform.customer.entity.Customer;
import com.financialplatform.customer.entity.CustomerKyc;
import com.financialplatform.customer.entity.KycStatus;
import com.financialplatform.customer.exception.KycCustomerInactiveException;
import com.financialplatform.customer.exception.CustomerKycNotFoundException;
import com.financialplatform.customer.exception.CustomerNotFoundException;
import com.financialplatform.customer.exception.DuplicateCustomerKycException;
import com.financialplatform.customer.mapper.CustomerKycMapper;
import com.financialplatform.customer.repository.CustomerKycRepository;
import com.financialplatform.customer.repository.CustomerRepository;
import com.financialplatform.customer.entity.CustomerStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerKycService {

    private final CustomerKycRepository customerKycRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public CustomerKycResponse createKyc(
            Long customerId,
            CustomerKycRequest request) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new CustomerNotFoundException(customerId)
                );

        if (customer.getCustomerStatus() != CustomerStatus.ACTIVE) {
            throw new KycCustomerInactiveException(
                    "KYC can be created only for active customers"
            );
        }

        if (customerKycRepository.existsByCustomerId(customerId)) {
            throw new DuplicateCustomerKycException(
                    "KYC already exists for customer ID: " + customerId
            );
        }

        LocalDateTime now = LocalDateTime.now();

        CustomerKyc kyc = CustomerKyc.builder()
                .customerId(customerId)
                .kycStatus(KycStatus.PENDING)
                .documentType(request.documentType())
                .documentNumber(request.documentNumber().trim())
                .createdAt(now)
                .updatedAt(now)
                .build();

        CustomerKyc savedKyc = customerKycRepository.save(kyc);

        log.info(
                "KYC created for customerId={}, kycId={}",
                customerId,
                savedKyc.getKycId()
        );

        return CustomerKycMapper.toResponse(savedKyc);
    }

    public CustomerKycResponse getKycByCustomerId(Long customerId) {

        CustomerKyc kyc = customerKycRepository
                .findByCustomerId(customerId)
                .orElseThrow(() ->
                        new CustomerKycNotFoundException(
                                "KYC not found for customer ID: " + customerId
                        )
                );

        return CustomerKycMapper.toResponse(kyc);
    }

    @Transactional
    public CustomerKycResponse updateKycStatus(
            Long customerId,
            CustomerKycStatusRequest request) {

        CustomerKyc kyc = customerKycRepository
                .findByCustomerId(customerId)
                .orElseThrow(() ->
                        new CustomerKycNotFoundException(
                                "KYC not found for customer ID: " + customerId
                        )
                );

        KycStatus newStatus = request.kycStatus();

        kyc.setKycStatus(newStatus);
        kyc.setLastReviewedAt(LocalDateTime.now());
        kyc.setUpdatedAt(LocalDateTime.now());

        if (newStatus == KycStatus.VERIFIED) {
            kyc.setVerifiedAt(LocalDateTime.now());
        } else {
            kyc.setVerifiedAt(null);
        }

        CustomerKyc updatedKyc = customerKycRepository.save(kyc);

        log.info(
                "KYC status updated for customerId={}, status={}",
                customerId,
                newStatus
        );

        return CustomerKycMapper.toResponse(updatedKyc);
    }
}