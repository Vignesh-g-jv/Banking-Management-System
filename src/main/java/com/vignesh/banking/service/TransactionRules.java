package com.vignesh.banking.service;

import com.vignesh.banking.exception.InvalidTransactionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The money rules every transaction must pass, kept in one place so the API,
 * the services and the tests all agree on them:
 * <ul>
 *   <li>the amount must be greater than zero</li>
 *   <li>at most 2 decimal places</li>
 *   <li>no more than the configured per-transaction limit (bank.max-transaction-amount)</li>
 * </ul>
 */
@Component
public class TransactionRules {

    private final BigDecimal maxAmount;

    public TransactionRules(@Value("${bank.max-transaction-amount:1000000}") BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    /** Validates a deposit, withdrawal or transfer amount and returns it with scale 2. */
    public BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidTransactionException("Amount is required");
        }
        if (amount.signum() <= 0) {
            throw new InvalidTransactionException("Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidTransactionException("Amount cannot have more than 2 decimal places");
        }
        if (amount.compareTo(maxAmount) > 0) {
            throw new InvalidTransactionException(
                    "Amount exceeds the limit of " + maxAmount.toPlainString() + " per transaction");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Opening deposits are optional: null or zero means an empty account. */
    public BigDecimal validateOpeningDeposit(BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (amount.signum() < 0) {
            throw new InvalidTransactionException("Opening deposit cannot be negative");
        }
        return validateAmount(amount);
    }
}
