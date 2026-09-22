package com.financialplatform.transaction.specification;

import com.financialplatform.transaction.entity.Transaction;
import com.financialplatform.transaction.entity.TransactionStatus;
import com.financialplatform.transaction.entity.TransactionType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class TransactionSpecification {

    private TransactionSpecification() {
    }

    public static Specification<Transaction> withFilters(
            Collection<Long> accountIds,
            TransactionType transactionType,
            TransactionStatus transactionStatus,
            LocalDateTime fromDate,
            LocalDateTime toDate) {

        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates =
                    new ArrayList<>();

            if (accountIds != null
                    && !accountIds.isEmpty()) {

                Predicate sourceAccountPredicate =
                        root.get("sourceAccountId")
                                .in(accountIds);

                Predicate targetAccountPredicate =
                        root.get("targetAccountId")
                                .in(accountIds);

                predicates.add(
                        criteriaBuilder.or(
                                sourceAccountPredicate,
                                targetAccountPredicate
                        )
                );
            }

            if (transactionType != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("transactionType"),
                                transactionType
                        )
                );
            }

            if (transactionStatus != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("transactionStatus"),
                                transactionStatus
                        )
                );
            }

            if (fromDate != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                fromDate
                        )
                );
            }

            if (toDate != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("createdAt"),
                                toDate
                        )
                );
            }

            return criteriaBuilder.and(
                    predicates.toArray(
                            Predicate[]::new
                    )
            );
        };
    }
}