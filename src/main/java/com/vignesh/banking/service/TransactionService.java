package com.vignesh.banking.service;

import com.vignesh.banking.dto.TransactionResponse;
import com.vignesh.banking.dto.TransferResponse;
import com.vignesh.banking.exception.AccountNotFoundException;
import com.vignesh.banking.exception.AccountStateException;
import com.vignesh.banking.exception.InsufficientFundsException;
import com.vignesh.banking.exception.InvalidTransactionException;
import com.vignesh.banking.model.Account;
import com.vignesh.banking.model.AccountStatus;
import com.vignesh.banking.model.BankTransaction;
import com.vignesh.banking.model.TransactionType;
import com.vignesh.banking.repository.AccountRepository;
import com.vignesh.banking.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Deposits, withdrawals, transfers and history. Every money movement runs inside one
 * database transaction with row locks on the accounts involved, so a failure part-way
 * through rolls everything back and concurrent requests cannot corrupt a balance.
 */
@Service
public class TransactionService {

    private static final int MAX_HISTORY_ROWS = 500;

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final TransactionRules rules;

    public TransactionService(AccountRepository accounts, TransactionRepository transactions, TransactionRules rules) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.rules = rules;
    }

    @Transactional
    public TransactionResponse deposit(String accountNumber, BigDecimal amount, String description) {
        BigDecimal value = rules.validateAmount(amount);
        Account account = lockActive(accountNumber);

        account.setBalance(account.getBalance().add(value));
        accounts.save(account);

        BankTransaction tx = transactions.save(BankTransaction.of(account, TransactionType.DEPOSIT, value,
                noteOrDefault(description, "Deposit"), null, newReference()));
        return TransactionResponse.from(tx);
    }

    @Transactional
    public TransactionResponse withdraw(String accountNumber, BigDecimal amount, String description) {
        BigDecimal value = rules.validateAmount(amount);
        Account account = lockActive(accountNumber);

        requireSufficientFunds(account, value);
        account.setBalance(account.getBalance().subtract(value));
        accounts.save(account);

        BankTransaction tx = transactions.save(BankTransaction.of(account, TransactionType.WITHDRAWAL, value,
                noteOrDefault(description, "Withdrawal"), null, newReference()));
        return TransactionResponse.from(tx);
    }

    @Transactional
    public TransferResponse transfer(String fromAccount, String toAccount, BigDecimal amount, String description) {
        String from = fromAccount.trim();
        String to = toAccount.trim();
        if (from.equals(to)) {
            throw new InvalidTransactionException("Source and destination accounts must be different");
        }
        BigDecimal value = rules.validateAmount(amount);

        // Lock in a fixed order (lowest account number first) so two opposite transfers
        // between the same pair of accounts cannot deadlock each other.
        String first = from.compareTo(to) < 0 ? from : to;
        String second = first.equals(from) ? to : from;
        Account firstAccount = lock(first);
        Account secondAccount = lock(second);
        Account source = firstAccount.getAccountNumber().equals(from) ? firstAccount : secondAccount;
        Account destination = source == firstAccount ? secondAccount : firstAccount;

        requireActive(source);
        requireActive(destination);
        requireSufficientFunds(source, value);

        source.setBalance(source.getBalance().subtract(value));
        destination.setBalance(destination.getBalance().add(value));
        accounts.save(source);
        accounts.save(destination);

        String reference = newReference();
        BankTransaction out = transactions.save(BankTransaction.of(source, TransactionType.TRANSFER_OUT, value,
                noteOrDefault(description, "Transfer to " + to), to, reference));
        transactions.save(BankTransaction.of(destination, TransactionType.TRANSFER_IN, value,
                noteOrDefault(description, "Transfer from " + from), from, reference));

        return new TransferResponse(reference, from, to, value,
                source.getBalance(), destination.getBalance(), out.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> history(String accountNumber, TransactionType type, int limit) {
        String number = accountNumber.trim();
        if (!accounts.existsByAccountNumber(number)) {
            throw new AccountNotFoundException(number);
        }
        Pageable page = PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_HISTORY_ROWS));
        List<BankTransaction> rows = type == null
                ? transactions.findByAccount_AccountNumberOrderByCreatedAtDescIdDesc(number, page)
                : transactions.findByAccount_AccountNumberAndTypeOrderByCreatedAtDescIdDesc(number, type, page);
        return rows.stream().map(TransactionResponse::from).toList();
    }

    private Account lock(String accountNumber) {
        return accounts.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private Account lockActive(String accountNumber) {
        Account account = lock(accountNumber.trim());
        requireActive(account);
        return account;
    }

    private void requireActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStateException("Account " + account.getAccountNumber()
                    + " is closed and cannot take part in transactions");
        }
    }

    private void requireSufficientFunds(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(account.getAccountNumber(), account.getBalance(), amount);
        }
    }

    private static String noteOrDefault(String note, String fallback) {
        return note == null || note.isBlank() ? fallback : note.trim();
    }

    private static String newReference() {
        return UUID.randomUUID().toString();
    }
}
