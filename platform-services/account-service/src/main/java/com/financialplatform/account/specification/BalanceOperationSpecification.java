package com.financialplatform.account.specification;

import com.financialplatform.account.entity.BalanceOperation;
import com.financialplatform.account.entity.BalanceOperationType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class BalanceOperationSpecification {

    private BalanceOperationSpecification() {
    }

    public static Specification<BalanceOperation> withFilters(
            Long accountId,
            BalanceOperationType operationType,
            LocalDateTime fromDate,
            LocalDateTime toDate) {

        return hasAccountId(accountId)
                .and(hasOperationType(operationType))
                .and(createdAtOnOrAfter(fromDate))
                .and(createdAtOnOrBefore(toDate));
    }

    private static Specification<BalanceOperation> hasAccountId(
            Long accountId) {

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("accountId"),
                        accountId
                );
    }

    private static Specification<BalanceOperation> hasOperationType(
            BalanceOperationType operationType) {

        return (root, query, criteriaBuilder) -> {

            if (operationType == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("operationType"),
                    operationType
            );
        };
    }

    private static Specification<BalanceOperation> createdAtOnOrAfter(
            LocalDateTime fromDate) {

        return (root, query, criteriaBuilder) -> {

            if (fromDate == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.greaterThanOrEqualTo(
                    root.get("createdAt"),
                    fromDate
            );
        };
    }

    private static Specification<BalanceOperation> createdAtOnOrBefore(
            LocalDateTime toDate) {

        return (root, query, criteriaBuilder) -> {

            if (toDate == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.lessThanOrEqualTo(
                    root.get("createdAt"),
                    toDate
            );
        };
    }
}