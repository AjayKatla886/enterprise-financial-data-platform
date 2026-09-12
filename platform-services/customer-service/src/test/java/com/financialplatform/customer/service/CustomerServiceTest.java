package com.financialplatform.customer.service;

import com.financialplatform.customer.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.financialplatform.customer.dto.CustomerRequest;
import com.financialplatform.customer.dto.CustomerResponse;
import com.financialplatform.customer.entity.Customer;
import com.financialplatform.customer.entity.CustomerStatus;
import org.junit.jupiter.api.Test;
import com.financialplatform.customer.exception.CustomerBusinessException;
import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.customer.dto.CustomerPatchRequest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void shouldCreateCustomerSuccessfully() {

        CustomerRequest request =
                new CustomerRequest(
                        "  Ajay  ",
                        "  Katla  ",
                        "  AJAY.TEST@EXAMPLE.COM  ",
                        "2145551234",
                        LocalDate.of(1995, 5, 10)
                );

        when(customerRepository.existsByEmailIgnoreCase(
                "  AJAY.TEST@EXAMPLE.COM  "
        )).thenReturn(false);

        when(customerRepository.saveAndFlush(any(Customer.class)))
                .thenAnswer(invocation -> {

                    Customer customer = invocation.getArgument(0);

                    customer.setCustomerId(10L);
                    customer.setCustomerNumber("000010");

                    return customer;
                });

        CustomerResponse response =
                customerService.createCustomer(request);

        assertNotNull(response);

        assertEquals(10L, response.customerId());
        assertEquals("000010", response.customerNumber());

        assertEquals("Ajay", response.firstName());
        assertEquals("Katla", response.lastName());

        assertEquals(
                "ajay.test@example.com",
                response.email()
        );

        assertEquals(
                CustomerStatus.ACTIVE.name(),
                response.customerStatus()
        );

        assertNotNull(response.createdAt());
        assertNotNull(response.updatedAt());

        verify(customerRepository)
                .existsByEmailIgnoreCase(
                        "  AJAY.TEST@EXAMPLE.COM  "
                );

        verify(customerRepository)
                .saveAndFlush(any(Customer.class));

        verify(entityManager)
                .refresh(any(Customer.class));
    }
    @Test
    void shouldRejectCustomerCreationWhenEmailAlreadyExists() {

        CustomerRequest request =
                new CustomerRequest(
                        "Ajay",
                        "Katla",
                        "ajay.test@example.com",
                        "2145551234",
                        LocalDate.of(1995, 5, 10)
                );

        when(customerRepository.existsByEmailIgnoreCase(
                "ajay.test@example.com"
        )).thenReturn(true);

        CustomerBusinessException exception =
                assertThrows(
                        CustomerBusinessException.class,
                        () -> customerService.createCustomer(request)
                );

        assertEquals(
                ErrorCode.DUPLICATE_CUSTOMER,
                exception.getErrorCode()
        );

        verify(customerRepository)
                .existsByEmailIgnoreCase(
                        "ajay.test@example.com"
                );

        verify(customerRepository, never())
                .saveAndFlush(any(Customer.class));

        verify(entityManager, never())
                .refresh(any(Customer.class));
    }
    @Test
    void shouldGetCustomerByIdSuccessfully() {

        Customer customer = new Customer();

        customer.setCustomerId(10L);
        customer.setCustomerNumber("000010");
        customer.setFirstName("Ajay");
        customer.setLastName("Katla");
        customer.setEmail("ajay.test@example.com");
        customer.setPhoneNumber("2145551234");
        customer.setDateOfBirth(LocalDate.of(1995, 5, 10));
        customer.setCustomerStatus(CustomerStatus.ACTIVE);

        when(customerRepository.findById(10L))
                .thenReturn(java.util.Optional.of(customer));

        CustomerResponse response =
                customerService.getCustomerById(10L);

        assertNotNull(response);

        assertEquals(10L, response.customerId());
        assertEquals("000010", response.customerNumber());
        assertEquals("Ajay", response.firstName());
        assertEquals("Katla", response.lastName());
        assertEquals("ajay.test@example.com", response.email());
        assertEquals("2145551234", response.phoneNumber());
        assertEquals(
                CustomerStatus.ACTIVE.name(),
                response.customerStatus()
        );

        verify(customerRepository).findById(10L);
    }
    @Test
    void shouldThrowExceptionWhenCustomerNotFound() {

        when(customerRepository.findById(999L))
                .thenReturn(java.util.Optional.empty());

        CustomerBusinessException exception =
                assertThrows(
                        CustomerBusinessException.class,
                        () -> customerService.getCustomerById(999L)
                );

        assertEquals(
                ErrorCode.CUSTOMER_NOT_FOUND,
                exception.getErrorCode()
        );

        assertEquals(
                "Customer not found with ID: 999",
                exception.getMessage()
        );

        verify(customerRepository).findById(999L);
    }
    @Test
    void shouldDeactivateActiveCustomerSuccessfully() {

        Customer customer = new Customer();

        customer.setCustomerId(10L);
        customer.setCustomerNumber("000010");
        customer.setFirstName("Ajay");
        customer.setLastName("Katla");
        customer.setEmail("ajay.test@example.com");
        customer.setPhoneNumber("2145551234");
        customer.setDateOfBirth(LocalDate.of(1995, 5, 10));
        customer.setCustomerStatus(CustomerStatus.ACTIVE);

        when(customerRepository.findById(10L))
                .thenReturn(java.util.Optional.of(customer));

        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response =
                customerService.deactivateCustomer(10L);

        assertNotNull(response);

        assertEquals(
                CustomerStatus.INACTIVE.name(),
                response.customerStatus()
        );

        assertEquals(
                CustomerStatus.INACTIVE,
                customer.getCustomerStatus()
        );

        assertNotNull(customer.getUpdatedAt());

        verify(customerRepository).findById(10L);

        verify(customerRepository)
                .save(customer);
    }
    @Test
    void shouldRejectDeactivationWhenCustomerAlreadyInactive() {

        Customer customer = new Customer();

        customer.setCustomerId(10L);
        customer.setCustomerNumber("000010");
        customer.setFirstName("Ajay");
        customer.setLastName("Katla");
        customer.setEmail("ajay.test@example.com");
        customer.setCustomerStatus(CustomerStatus.INACTIVE);

        when(customerRepository.findById(10L))
                .thenReturn(java.util.Optional.of(customer));

        CustomerBusinessException exception =
                assertThrows(
                        CustomerBusinessException.class,
                        () -> customerService.deactivateCustomer(10L)
                );

        assertEquals(
                "Customer is already inactive",
                exception.getMessage()
        );

        verify(customerRepository).findById(10L);

        verify(customerRepository, never())
                .save(any(Customer.class));
    }
    @Test
    void shouldUpdateCustomerSuccessfully() {

        Customer existingCustomer = new Customer();

        existingCustomer.setCustomerId(10L);
        existingCustomer.setCustomerNumber("000010");
        existingCustomer.setFirstName("Ajay");
        existingCustomer.setLastName("Katla");
        existingCustomer.setEmail("old@example.com");
        existingCustomer.setPhoneNumber("2145551234");
        existingCustomer.setDateOfBirth(LocalDate.of(1995, 5, 10));
        existingCustomer.setCustomerStatus(CustomerStatus.ACTIVE);

        CustomerRequest request =
                new CustomerRequest(
                        "  Vijay  ",
                        "  Kumar  ",
                        "  NEW@EXAMPLE.COM  ",
                        "4695551234",
                        LocalDate.of(1994, 6, 15)
                );

        when(customerRepository.findById(10L))
                .thenReturn(java.util.Optional.of(existingCustomer));

        when(customerRepository.existsByEmailIgnoreCase(
                "  NEW@EXAMPLE.COM  "
        )).thenReturn(false);

        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response =
                customerService.updateCustomer(10L, request);

        assertNotNull(response);

        assertEquals("Vijay", response.firstName());
        assertEquals("Kumar", response.lastName());
        assertEquals("new@example.com", response.email());
        assertEquals("4695551234", response.phoneNumber());

        assertEquals(
                CustomerStatus.ACTIVE.name(),
                response.customerStatus()
        );

        assertNotNull(response.updatedAt());

        verify(customerRepository).findById(10L);

        verify(customerRepository)
                .existsByEmailIgnoreCase(
                        "  NEW@EXAMPLE.COM  "
                );

        verify(customerRepository)
                .save(existingCustomer);
    }
    @Test
    void shouldRejectUpdateWhenEmailAlreadyExists() {

        Customer existingCustomer = new Customer();

        existingCustomer.setCustomerId(10L);
        existingCustomer.setCustomerNumber("000010");
        existingCustomer.setFirstName("Ajay");
        existingCustomer.setLastName("Katla");
        existingCustomer.setEmail("old@example.com");
        existingCustomer.setCustomerStatus(CustomerStatus.ACTIVE);

        CustomerRequest request =
                new CustomerRequest(
                        "Ajay",
                        "Katla",
                        "existing@example.com",
                        "2145551234",
                        LocalDate.of(1995, 5, 10)
                );

        when(customerRepository.findById(10L))
                .thenReturn(java.util.Optional.of(existingCustomer));

        when(customerRepository.existsByEmailIgnoreCase(
                "existing@example.com"
        )).thenReturn(true);

        CustomerBusinessException exception =
                assertThrows(
                        CustomerBusinessException.class,
                        () -> customerService.updateCustomer(10L, request)
                );

        assertEquals(
                ErrorCode.DUPLICATE_CUSTOMER,
                exception.getErrorCode()
        );

        verify(customerRepository).findById(10L);

        verify(customerRepository)
                .existsByEmailIgnoreCase("existing@example.com");

        verify(customerRepository, never())
                .save(any(Customer.class));
    }
    @Test
    void shouldPatchCustomerSuccessfully() {

        Customer existingCustomer = new Customer();

        existingCustomer.setCustomerId(10L);
        existingCustomer.setCustomerNumber("000010");
        existingCustomer.setFirstName("Ajay");
        existingCustomer.setLastName("Katla");
        existingCustomer.setEmail("ajay@example.com");
        existingCustomer.setPhoneNumber("2145551234");
        existingCustomer.setDateOfBirth(
                LocalDate.of(1995, 5, 10)
        );
        existingCustomer.setCustomerStatus(
                CustomerStatus.ACTIVE
        );

        CustomerPatchRequest request =
                new CustomerPatchRequest(
                        "  Vijay  ",
                        null,
                        null,
                        "4695551234",
                        null
                );

        when(customerRepository.findById(10L))
                .thenReturn(
                        java.util.Optional.of(existingCustomer)
                );

        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(
                        invocation -> invocation.getArgument(0)
                );

        CustomerResponse response =
                customerService.patchCustomer(
                        10L,
                        request
                );

        assertNotNull(response);

        // Changed fields
        assertEquals(
                "Vijay",
                response.firstName()
        );

        assertEquals(
                "4695551234",
                response.phoneNumber()
        );

        // Unchanged fields
        assertEquals(
                "Katla",
                response.lastName()
        );

        assertEquals(
                "ajay@example.com",
                response.email()
        );

        assertEquals(
                LocalDate.of(1995, 5, 10),
                response.dateOfBirth()
        );

        assertEquals(
                CustomerStatus.ACTIVE.name(),
                response.customerStatus()
        );

        assertNotNull(response.updatedAt());

        verify(customerRepository)
                .findById(10L);

        verify(customerRepository)
                .save(existingCustomer);

        // Email wasn't included in PATCH,
        // so duplicate-email lookup should not happen.
        verify(customerRepository, never())
                .existsByEmailIgnoreCase(anyString());
    }
    @Test
    void shouldRejectPatchWhenEmailAlreadyExists() {

        Customer existingCustomer = new Customer();

        existingCustomer.setCustomerId(10L);
        existingCustomer.setCustomerNumber("000010");
        existingCustomer.setFirstName("Ajay");
        existingCustomer.setLastName("Katla");
        existingCustomer.setEmail("ajay@example.com");
        existingCustomer.setCustomerStatus(CustomerStatus.ACTIVE);

        CustomerPatchRequest request =
                new CustomerPatchRequest(
                        null,
                        null,
                        "existing@example.com",
                        null,
                        null
                );

        when(customerRepository.findById(10L))
                .thenReturn(java.util.Optional.of(existingCustomer));

        when(customerRepository.existsByEmailIgnoreCase(
                "existing@example.com"
        )).thenReturn(true);

        CustomerBusinessException exception =
                assertThrows(
                        CustomerBusinessException.class,
                        () -> customerService.patchCustomer(10L, request)
                );

        assertEquals(
                ErrorCode.DUPLICATE_CUSTOMER,
                exception.getErrorCode()
        );

        verify(customerRepository).findById(10L);

        verify(customerRepository)
                .existsByEmailIgnoreCase(
                        "existing@example.com"
                );

        verify(customerRepository, never())
                .save(any(Customer.class));
    }
}