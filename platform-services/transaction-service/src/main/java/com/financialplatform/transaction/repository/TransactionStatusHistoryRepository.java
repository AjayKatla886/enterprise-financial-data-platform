package com.financialplatform.transaction.repository;

import com.financialplatform.transaction.entity.TransactionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionStatusHistoryRepository
        extends JpaRepository<TransactionStatusHistory, Long> {

    List<TransactionStatusHistory>
    findByTransactionReferenceOrderByCreatedAtAscStatusHistoryIdAsc(
            String transactionReference
    );
}