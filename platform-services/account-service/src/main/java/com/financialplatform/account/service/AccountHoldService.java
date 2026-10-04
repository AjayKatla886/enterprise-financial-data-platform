package com.financialplatform.account.service;

import com.financialplatform.account.dto.AccountBalanceSummaryResponse;
import com.financialplatform.account.dto.AccountHoldRequest;
import com.financialplatform.account.entity.Account;
import com.financialplatform.account.entity.AccountHold;
import com.financialplatform.account.entity.AccountHoldStatus;
import com.financialplatform.account.entity.AccountStatus;
import com.financialplatform.account.exception.AccountBusinessException;
import com.financialplatform.account.repository.AccountHoldRepository;
import com.financialplatform.account.repository.AccountRepository;
import com.financialplatform.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountHoldService {

    private static final int MAX_HOLD_REFERENCE_LENGTH = 150;

    private final AccountRepository accountRepository;
    private final AccountHoldRepository accountHoldRepository;

    @Transactional
    public AccountHold createHold(
            Long accountId,
            String holdReference,
            AccountHoldRequest request) {

        validateAccountId(accountId);

        String normalizedHoldReference =
                validateAndNormalizeHoldReference(
                        holdReference
                );

        LocalDateTime now =
                LocalDateTime.now();

        validateExpiration(
                request.expiresAt(),
                now
        );

        /*
         * Locking the account serializes hold creation and balance
         * operations for this account. Two requests cannot reserve
         * the same available funds simultaneously.
         */
        Account account = accountRepository
                .findByIdForUpdate(accountId)
                .orElseThrow(() -> accountNotFound(accountId));

        String requestHash =
                generateRequestHash(
                        accountId,
                        request
                );

        AccountHold existingHold =
                accountHoldRepository
                        .findByHoldReference(
                                normalizedHoldReference
                        )
                        .orElse(null);

        if (existingHold != null) {
            return handleExistingHold(
                    existingHold,
                    requestHash
            );
        }

        validateAccountStatus(account);

        BigDecimal activeHoldAmount =
                defaultAmount(
                        accountHoldRepository
                                .sumActiveHoldAmount(
                                        accountId,
                                        AccountHoldStatus.ACTIVE,
                                        now
                                )
                );

        BigDecimal availableBalance =
                account.getBalance()
                        .subtract(activeHoldAmount);

        if (availableBalance
                .compareTo(request.amount()) < 0) {

            throw new AccountBusinessException(
                    ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE,
                    "Insufficient available balance for account ID: "
                            + accountId
            );
        }

        AccountHold hold =
                AccountHold.builder()
                        .holdReference(
                                normalizedHoldReference
                        )
                        .transactionReference(
                                request.transactionReference()
                                        .trim()
                        )
                        .accountId(accountId)
                        .amount(request.amount())
                        .holdStatus(
                                AccountHoldStatus.ACTIVE
                        )
                        .requestHash(requestHash)
                        .description(
                                normalizeDescription(
                                        request.description()
                                )
                        )
                        .expiresAt(request.expiresAt())
                        .createdAt(now)
                        .updatedAt(now)
                        .resolvedAt(null)
                        .build();

        AccountHold savedHold =
                accountHoldRepository
                        .saveAndFlush(hold);

        log.info(
                "Account hold created. accountHoldId={}, "
                        + "holdReference={}, accountId={}, amount={}, "
                        + "ledgerBalance={}, activeHoldAmountBefore={}, "
                        + "availableBalanceBefore={}",
                savedHold.getAccountHoldId(),
                savedHold.getHoldReference(),
                accountId,
                savedHold.getAmount(),
                account.getBalance(),
                activeHoldAmount,
                availableBalance
        );

        return savedHold;
    }

    @Transactional(readOnly = true)
    public AccountHold getByHoldReference(
            String holdReference) {

        String normalizedHoldReference =
                validateAndNormalizeHoldReference(
                        holdReference
                );

        return accountHoldRepository
                .findByHoldReference(
                        normalizedHoldReference
                )
                .orElseThrow(() ->
                        new AccountBusinessException(
                                ErrorCode.ACCOUNT_HOLD_NOT_FOUND,
                                "Account hold not found with reference: "
                                        + normalizedHoldReference
                        )
                );
    }

    @Transactional(readOnly = true)
    public AccountBalanceSummaryResponse getBalanceSummary(
            Long accountId) {

        validateAccountId(accountId);

        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> accountNotFound(accountId));

        LocalDateTime calculatedAt =
                LocalDateTime.now();

        BigDecimal activeHoldAmount =
                defaultAmount(
                        accountHoldRepository
                                .sumActiveHoldAmount(
                                        accountId,
                                        AccountHoldStatus.ACTIVE,
                                        calculatedAt
                                )
                );

        BigDecimal availableBalance =
                account.getBalance()
                        .subtract(activeHoldAmount);

        if (availableBalance
                .compareTo(BigDecimal.ZERO) < 0) {

            log.error(
                    "Account available balance is negative. "
                            + "accountId={}, ledgerBalance={}, "
                            + "activeHoldAmount={}, availableBalance={}",
                    accountId,
                    account.getBalance(),
                    activeHoldAmount,
                    availableBalance
            );

            throw new IllegalStateException(
                    "Account hold data is inconsistent"
            );
        }

        return new AccountBalanceSummaryResponse(
                account.getAccountId(),
                account.getAccountNumber(),
                account.getAccountType().name(),
                account.getBalance(),
                activeHoldAmount,
                availableBalance,
                calculatedAt
        );
    }

    private AccountHold handleExistingHold(
            AccountHold existingHold,
            String requestHash) {

        if (!existingHold
                .getRequestHash()
                .equals(requestHash)) {

            throw new AccountBusinessException(
                    ErrorCode.ACCOUNT_HOLD_IDEMPOTENCY_CONFLICT,
                    "Hold reference was already used "
                            + "with different request data"
            );
        }

        log.info(
                "Returning existing idempotent account hold. "
                        + "accountHoldId={}, holdReference={}, "
                        + "accountId={}, status={}",
                existingHold.getAccountHoldId(),
                existingHold.getHoldReference(),
                existingHold.getAccountId(),
                existingHold.getHoldStatus()
        );

        return existingHold;
    }

    private void validateAccountStatus(
            Account account) {

        if (account.getAccountStatus()
                != AccountStatus.ACTIVE) {

            throw new AccountBusinessException(
                    ErrorCode.INVALID_ACCOUNT_STATE,
                    "Account hold requires an active account. "
                            + "accountId="
                            + account.getAccountId()
            );
        }
    }

    private void validateExpiration(
            LocalDateTime expiresAt,
            LocalDateTime now) {

        if (expiresAt == null
                || !expiresAt.isAfter(now)) {

            throw new AccountBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Hold expiration time must be in the future"
            );
        }
    }

    private void validateAccountId(
            Long accountId) {

        if (accountId == null
                || accountId <= 0) {

            throw new AccountBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Account ID must be greater than zero"
            );
        }
    }

    private String validateAndNormalizeHoldReference(
            String holdReference) {

        if (holdReference == null
                || holdReference.isBlank()) {

            throw new AccountBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Hold-Reference header is required"
            );
        }

        String normalizedReference =
                holdReference.trim();

        if (normalizedReference.length()
                > MAX_HOLD_REFERENCE_LENGTH) {

            throw new AccountBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Hold-Reference must not exceed "
                            + MAX_HOLD_REFERENCE_LENGTH
                            + " characters"
            );
        }

        return normalizedReference;
    }

    private String generateRequestHash(
            Long accountId,
            AccountHoldRequest request) {

        String canonicalRequest =
                String.join(
                        "|",
                        accountId.toString(),
                        request.transactionReference()
                                .trim(),
                        normalizeAmount(
                                request.amount()
                        ),
                        normalizeDescription(
                                request.description()
                        ),
                        request.expiresAt()
                                .toString()
                );

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            canonicalRequest.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of()
                    .formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    ex
            );
        }
    }

    private String normalizeAmount(
            BigDecimal amount) {

        return amount.stripTrailingZeros()
                .toPlainString();
    }

    private String normalizeDescription(
            String description) {

        if (description == null) {
            return "";
        }

        return description.trim();
    }

    private BigDecimal defaultAmount(
            BigDecimal amount) {

        return amount == null
                ? BigDecimal.ZERO
                : amount;
    }

    private AccountBusinessException accountNotFound(
            Long accountId) {

        return new AccountBusinessException(
                ErrorCode.ACCOUNT_NOT_FOUND,
                "Account not found with ID: "
                        + accountId
        );
    }
}