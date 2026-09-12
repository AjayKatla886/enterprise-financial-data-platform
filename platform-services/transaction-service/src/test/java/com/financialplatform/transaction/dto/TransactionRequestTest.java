package com.financialplatform.transaction.dto;

import com.financialplatform.transaction.entity.TransactionType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TransactionRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void shouldAcceptValidDepositWithdrawalAndTransfer() {
        assertTrue(validator.validate(
                request(TransactionType.DEPOSIT, null, 21L, "100.00", "USD")
        ).isEmpty());

        assertTrue(validator.validate(
                request(TransactionType.WITHDRAWAL, 21L, null, "100.00", "USD")
        ).isEmpty());

        assertTrue(validator.validate(
                request(TransactionType.TRANSFER, 21L, 22L, "100.00", "USD")
        ).isEmpty());
    }

    @Test
    void shouldRejectInvalidAccountCombinations() {
        assertInvalidCombination(TransactionType.DEPOSIT, 21L, 22L);
        assertInvalidCombination(TransactionType.DEPOSIT, null, null);
        assertInvalidCombination(TransactionType.WITHDRAWAL, 21L, 22L);
        assertInvalidCombination(TransactionType.WITHDRAWAL, null, null);
        assertInvalidCombination(TransactionType.TRANSFER, null, 22L);
        assertInvalidCombination(TransactionType.TRANSFER, 21L, null);
        assertInvalidCombination(TransactionType.TRANSFER, 21L, 21L);
    }

    @Test
    void shouldRejectZeroNegativeAndOverprecisionAmounts() {
        assertInvalidField(
                request(TransactionType.DEPOSIT, null, 21L, "0.00", "USD"),
                "amount"
        );

        assertInvalidField(
                request(TransactionType.DEPOSIT, null, 21L, "-1.00", "USD"),
                "amount"
        );

        assertInvalidField(
                request(TransactionType.DEPOSIT, null, 21L, "1.001", "USD"),
                "amount"
        );

        assertInvalidField(
                request(
                        TransactionType.DEPOSIT,
                        null,
                        21L,
                        "100000000000000000.00",
                        "USD"
                ),
                "amount"
        );
    }

    @Test
    void shouldRejectUnsupportedCurrency() {
        assertInvalidField(
                request(TransactionType.DEPOSIT, null, 21L, "100.00", "EUR"),
                "currency"
        );
    }

    @Test
    void shouldRejectNonpositiveAccountIds() {
        assertInvalidField(
                request(TransactionType.DEPOSIT, null, 0L, "100.00", "USD"),
                "targetAccountId"
        );

        assertInvalidField(
                request(TransactionType.WITHDRAWAL, -1L, null, "100.00", "USD"),
                "sourceAccountId"
        );
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        TransactionRequest request =
                new TransactionRequest(null, null, null, null, null, null);

        assertInvalidField(request, "transactionType");
        assertInvalidField(request, "amount");
        assertInvalidField(request, "currency");
    }

    @Test
    void shouldRejectDescriptionLongerThan255Characters() {
        TransactionRequest request = new TransactionRequest(
                TransactionType.DEPOSIT,
                null,
                21L,
                new BigDecimal("100.00"),
                "USD",
                "a".repeat(256)
        );

        assertInvalidField(request, "description");
    }

    private void assertInvalidCombination(
            TransactionType type,
            Long sourceId,
            Long targetId) {

        assertInvalidField(
                request(type, sourceId, targetId, "100.00", "USD"),
                "accountCombinationValid"
        );
    }

    private void assertInvalidField(
            TransactionRequest request,
            String field) {

        assertTrue(
                validator.validate(request).stream()
                        .anyMatch(violation ->
                                violation.getPropertyPath()
                                        .toString()
                                        .equals(field)),
                "Expected a validation error for: " + field
        );
    }

    private TransactionRequest request(
            TransactionType type,
            Long sourceId,
            Long targetId,
            String amount,
            String currency) {

        return new TransactionRequest(
                type,
                sourceId,
                targetId,
                new BigDecimal(amount),
                currency,
                null
        );
    }
}