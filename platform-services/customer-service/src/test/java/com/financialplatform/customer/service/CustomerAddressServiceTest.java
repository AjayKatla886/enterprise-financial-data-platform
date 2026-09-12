package com.financialplatform.customer.service;

import com.financialplatform.customer.dto.CustomerAddressRequest;
import com.financialplatform.customer.dto.CustomerAddressResponse;
import com.financialplatform.customer.entity.Customer;
import com.financialplatform.customer.entity.CustomerAddress;
import com.financialplatform.customer.entity.CustomerStatus;
import com.financialplatform.customer.repository.CustomerAddressRepository;
import com.financialplatform.customer.repository.CustomerRepository;
import com.financialplatform.customer.exception.CustomerBusinessException;
import com.financialplatform.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import java.util.List;
import com.financialplatform.customer.entity.AddressType;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerAddressServiceTest {

    @Mock
    private CustomerAddressRepository customerAddressRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerAddressService customerAddressService;

    @Test
    void shouldAddAddressForActiveCustomerSuccessfully() {

        Customer customer = new Customer();
        customer.setCustomerId(10L);
        customer.setCustomerStatus(CustomerStatus.ACTIVE);

        CustomerAddressRequest request =
                new CustomerAddressRequest(
                        AddressType.HOME,
                        "123 Main Street",
                        null,
                        "Dallas",
                        "TX",
                        "75001",
                        "USA"
                );

        when(customerRepository.findById(10L))
                .thenReturn(Optional.of(customer));

        when(customerAddressRepository
                .findByCustomerIdAndAddressTypeAndCurrentTrue(
                        10L,
                        AddressType.HOME
                ))
                .thenReturn(Optional.empty());

        when(customerAddressRepository.save(any(CustomerAddress.class)))
                .thenAnswer(invocation -> {

                    CustomerAddress address =
                            invocation.getArgument(0);

                    address.setAddressId(100L);

                    return address;
                });

        CustomerAddressResponse response =
                customerAddressService.addAddress(
                        10L,
                        request
                );

        assertNotNull(response);

        assertEquals(100L, response.addressId());
        assertEquals(10L, response.customerId());

        assertEquals(
                AddressType.HOME.name(),
                response.addressType()
        );

        assertEquals(
                "123 Main Street",
                response.addressLine1()
        );

        assertEquals("Dallas", response.city());
        assertEquals("TX", response.state());
        assertEquals("75001", response.postalCode());
        assertEquals("USA", response.country());

        assertTrue(response.current());
        assertNotNull(response.validFrom());
        assertNull(response.validTo());

        verify(customerRepository)
                .findById(10L);

        verify(customerAddressRepository)
                .findByCustomerIdAndAddressTypeAndCurrentTrue(
                        10L,
                        AddressType.HOME
                );

        verify(customerAddressRepository)
                .save(any(CustomerAddress.class));
    }
    @Test
    void shouldRejectAddressForInactiveCustomer() {

        Customer customer = new Customer();
        customer.setCustomerId(10L);
        customer.setCustomerStatus(CustomerStatus.INACTIVE);

        CustomerAddressRequest request =
                new CustomerAddressRequest(
                        AddressType.HOME,
                        "123 Main Street",
                        null,
                        "Dallas",
                        "TX",
                        "75001",
                        "USA"
                );

        when(customerRepository.findById(10L))
                .thenReturn(Optional.of(customer));

        CustomerBusinessException exception =
                assertThrows(
                        CustomerBusinessException.class,
                        () -> customerAddressService.addAddress(10L, request)
                );

        assertEquals(
                ErrorCode.CUSTOMER_INACTIVE,
                exception.getErrorCode()
        );

        verify(customerRepository)
                .findById(10L);

        verifyNoInteractions(customerAddressRepository);
    }
    @Test
    void shouldRejectDuplicateCurrentAddress() {

        Customer customer = new Customer();
        customer.setCustomerId(10L);
        customer.setCustomerStatus(CustomerStatus.ACTIVE);

        CustomerAddress existingAddress =
                CustomerAddress.builder()
                        .addressId(100L)
                        .customerId(10L)
                        .addressType(AddressType.HOME)
                        .addressLine1("123 Main Street")
                        .addressLine2(null)
                        .city("Dallas")
                        .state("TX")
                        .postalCode("75001")
                        .country("USA")
                        .current(true)
                        .build();

        CustomerAddressRequest request =
                new CustomerAddressRequest(
                        AddressType.HOME,
                        "123 Main Street",
                        null,
                        "Dallas",
                        "TX",
                        "75001",
                        "USA"
                );

        when(customerRepository.findById(10L))
                .thenReturn(Optional.of(customer));

        when(customerAddressRepository
                .findByCustomerIdAndAddressTypeAndCurrentTrue(
                        10L,
                        AddressType.HOME
                ))
                .thenReturn(Optional.of(existingAddress));

        CustomerBusinessException exception =
                assertThrows(
                        CustomerBusinessException.class,
                        () -> customerAddressService.addAddress(10L, request)
                );

        assertEquals(
                ErrorCode.DUPLICATE_ADDRESS,
                exception.getErrorCode()
        );

        verify(customerRepository)
                .findById(10L);

        verify(customerAddressRepository)
                .findByCustomerIdAndAddressTypeAndCurrentTrue(
                        10L,
                        AddressType.HOME
                );

        verify(customerAddressRepository, never())
                .save(any(CustomerAddress.class));
    }
    @Test
    void shouldReplaceCurrentAddressAndMoveOldAddressToHistory() {

        Customer customer = new Customer();
        customer.setCustomerId(10L);
        customer.setCustomerStatus(CustomerStatus.ACTIVE);

        CustomerAddress existingAddress =
                CustomerAddress.builder()
                        .addressId(100L)
                        .customerId(10L)
                        .addressType(AddressType.HOME)
                        .addressLine1("123 Main Street")
                        .city("Dallas")
                        .state("TX")
                        .postalCode("75001")
                        .country("USA")
                        .current(true)
                        .validFrom(LocalDateTime.now().minusMonths(6))
                        .createdAt(LocalDateTime.now().minusMonths(6))
                        .updatedAt(LocalDateTime.now().minusMonths(6))
                        .build();

        CustomerAddressRequest request =
                new CustomerAddressRequest(
                        AddressType.HOME,
                        "500 New Street",
                        null,
                        "Irving",
                        "TX",
                        "75039",
                        "USA"
                );

        when(customerRepository.findById(10L))
                .thenReturn(Optional.of(customer));

        when(customerAddressRepository
                .findByCustomerIdAndAddressTypeAndCurrentTrue(
                        10L,
                        AddressType.HOME
                ))
                .thenReturn(Optional.of(existingAddress));

        when(customerAddressRepository.save(any(CustomerAddress.class)))
                .thenAnswer(invocation -> {

                    CustomerAddress address = invocation.getArgument(0);

                    if (address.getAddressId() == null) {
                        address.setAddressId(101L);
                    }

                    return address;
                });

        CustomerAddressResponse response =
                customerAddressService.addAddress(
                        10L,
                        request
                );

        // Old address becomes historical
        assertFalse(existingAddress.isCurrent());
        assertNotNull(existingAddress.getValidTo());
        assertNotNull(existingAddress.getUpdatedAt());

        // New address becomes current
        assertNotNull(response);
        assertEquals(101L, response.addressId());
        assertEquals(10L, response.customerId());
        assertEquals("HOME", response.addressType());

        assertEquals(
                "500 New Street",
                response.addressLine1()
        );

        assertEquals("Irving", response.city());
        assertTrue(response.current());

        assertNotNull(response.validFrom());
        assertNull(response.validTo());

        // One save for old address + one save for new address
        verify(customerAddressRepository, times(2))
                .save(any(CustomerAddress.class));
    }
    @Test
    void shouldGetAddressHistorySuccessfully() {

        CustomerAddress oldAddress =
                CustomerAddress.builder()
                        .addressId(100L)
                        .customerId(10L)
                        .addressType(AddressType.HOME)
                        .addressLine1("123 Main Street")
                        .city("Dallas")
                        .state("TX")
                        .postalCode("75001")
                        .country("USA")
                        .current(false)
                        .validFrom(LocalDateTime.now().minusMonths(6))
                        .validTo(LocalDateTime.now().minusMonths(1))
                        .createdAt(LocalDateTime.now().minusMonths(6))
                        .updatedAt(LocalDateTime.now().minusMonths(1))
                        .build();

        CustomerAddress currentAddress =
                CustomerAddress.builder()
                        .addressId(101L)
                        .customerId(10L)
                        .addressType(AddressType.HOME)
                        .addressLine1("500 New Street")
                        .city("Irving")
                        .state("TX")
                        .postalCode("75039")
                        .country("USA")
                        .current(true)
                        .validFrom(LocalDateTime.now().minusMonths(1))
                        .validTo(null)
                        .createdAt(LocalDateTime.now().minusMonths(1))
                        .updatedAt(LocalDateTime.now().minusMonths(1))
                        .build();

        when(customerRepository.existsById(10L))
                .thenReturn(true);

        when(customerAddressRepository
                .findByCustomerIdOrderByValidFromDesc(10L))
                .thenReturn(List.of(currentAddress, oldAddress));

        List<CustomerAddressResponse> response =
                customerAddressService.getAddressHistory(10L);

        assertNotNull(response);
        assertEquals(2, response.size());

        // Repository returns newest address first
        assertEquals(101L, response.get(0).addressId());
        assertEquals("500 New Street", response.get(0).addressLine1());
        assertTrue(response.get(0).current());

        assertEquals(100L, response.get(1).addressId());
        assertEquals("123 Main Street", response.get(1).addressLine1());
        assertFalse(response.get(1).current());

        verify(customerRepository)
                .existsById(10L);

        verify(customerAddressRepository)
                .findByCustomerIdOrderByValidFromDesc(10L);
    }
    @Test
    void shouldGetCurrentAddressesSuccessfully() {

        CustomerAddress homeAddress =
                CustomerAddress.builder()
                        .addressId(101L)
                        .customerId(10L)
                        .addressType(AddressType.HOME)
                        .addressLine1("500 New Street")
                        .city("Irving")
                        .state("TX")
                        .postalCode("75039")
                        .country("USA")
                        .current(true)
                        .validFrom(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        CustomerAddress mailingAddress =
                CustomerAddress.builder()
                        .addressId(102L)
                        .customerId(10L)
                        .addressType(AddressType.MAILING)
                        .addressLine1("700 Office Road")
                        .city("Plano")
                        .state("TX")
                        .postalCode("75024")
                        .country("USA")
                        .current(true)
                        .validFrom(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        when(customerRepository.existsById(10L))
                .thenReturn(true);

        when(customerAddressRepository
                .findByCustomerIdAndCurrentTrue(10L))
                .thenReturn(List.of(homeAddress, mailingAddress));

        List<CustomerAddressResponse> response =
                customerAddressService.getCurrentAddresses(10L);

        assertNotNull(response);
        assertEquals(2, response.size());

        assertEquals(101L, response.get(0).addressId());
        assertEquals("HOME", response.get(0).addressType());
        assertEquals("Irving", response.get(0).city());
        assertTrue(response.get(0).current());

        assertEquals(102L, response.get(1).addressId());
        assertEquals("MAILING", response.get(1).addressType());
        assertEquals("Plano", response.get(1).city());
        assertTrue(response.get(1).current());

        verify(customerRepository)
                .existsById(10L);

        verify(customerAddressRepository)
                .findByCustomerIdAndCurrentTrue(10L);
    }
    @Test
    void shouldGetAddressByIdSuccessfully() {

        CustomerAddress address =
                CustomerAddress.builder()
                        .addressId(101L)
                        .customerId(10L)
                        .addressType(AddressType.HOME)
                        .addressLine1("500 New Street")
                        .addressLine2(null)
                        .city("Irving")
                        .state("TX")
                        .postalCode("75039")
                        .country("USA")
                        .current(true)
                        .validFrom(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        when(customerRepository.existsById(10L))
                .thenReturn(true);

        when(customerAddressRepository.findById(101L))
                .thenReturn(Optional.of(address));

        CustomerAddressResponse response =
                customerAddressService.getAddressById(
                        10L,
                        101L
                );

        assertNotNull(response);

        assertEquals(101L, response.addressId());
        assertEquals(10L, response.customerId());
        assertEquals("HOME", response.addressType());
        assertEquals("500 New Street", response.addressLine1());
        assertEquals("Irving", response.city());
        assertEquals("TX", response.state());
        assertEquals("75039", response.postalCode());
        assertEquals("USA", response.country());
        assertTrue(response.current());

        verify(customerRepository)
                .existsById(10L);

        verify(customerAddressRepository)
                .findById(101L);
    }
    @Test
    void shouldRejectAddressWhenItBelongsToAnotherCustomer() {

        CustomerAddress address =
                CustomerAddress.builder()
                        .addressId(101L)
                        .customerId(20L)
                        .addressType(AddressType.HOME)
                        .addressLine1("500 New Street")
                        .city("Irving")
                        .state("TX")
                        .postalCode("75039")
                        .country("USA")
                        .current(true)
                        .build();

        when(customerRepository.existsById(10L))
                .thenReturn(true);

        when(customerAddressRepository.findById(101L))
                .thenReturn(Optional.of(address));

        CustomerBusinessException exception =
                assertThrows(
                        CustomerBusinessException.class,
                        () -> customerAddressService.getAddressById(10L, 101L)
                );

        assertEquals(
                ErrorCode.ADDRESS_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(customerRepository)
                .existsById(10L);

        verify(customerAddressRepository)
                .findById(101L);
    }
    @Test
    void shouldThrowExceptionWhenAddressNotFound() {

        when(customerRepository.existsById(10L))
                .thenReturn(true);

        when(customerAddressRepository.findById(999L))
                .thenReturn(Optional.empty());

        CustomerBusinessException exception =
                assertThrows(
                        CustomerBusinessException.class,
                        () -> customerAddressService.getAddressById(10L, 999L)
                );

        assertEquals(
                ErrorCode.ADDRESS_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(customerRepository)
                .existsById(10L);

        verify(customerAddressRepository)
                .findById(999L);
    }
    @Test
    void shouldThrowExceptionWhenCustomerNotFoundForAddressLookup() {

        when(customerRepository.existsById(999L))
                .thenReturn(false);

        CustomerBusinessException exception =
                assertThrows(
                        CustomerBusinessException.class,
                        () -> customerAddressService.getAddressById(999L, 101L)
                );

        assertEquals(
                ErrorCode.CUSTOMER_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(customerRepository)
                .existsById(999L);

        verifyNoInteractions(customerAddressRepository);
    }
}